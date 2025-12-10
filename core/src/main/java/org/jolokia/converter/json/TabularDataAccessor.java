/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import javax.management.AttributeNotFoundException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.converter.object.Converter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.converter.object.TabularDataConverter;

/**
 * {@link org.jolokia.converter.json.ObjectAccessor} for {@link TabularData}.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class TabularDataAccessor implements org.jolokia.converter.json.ObjectAccessor {

    private final ObjectToOpenTypeConverter objectToOpenTypeConverter;

    public TabularDataAccessor(ObjectToOpenTypeConverter pObjectToOpenTypeConverter) {
        this.objectToOpenTypeConverter = pObjectToOpenTypeConverter;
    }

    @Override
    public Class<?> getType() {
        return TabularData.class;
    }

    /**
     * <p>Convert a {@link TabularData} to a {@link JSONObject}. There are 3 possible representations
     * of the returned JSON object depending on {@link TabularType}. These are described also in
     * {@link TabularDataConverter}.</p>
     *
     * <p><strong>#1.</strong> If the {@link TabularType} describes {@link java.util.Map} representation defined in
     * {@link javax.management.MXBean} specification, the resulting map is simple {@link JSONObject} with
     * key-value pairs, where each pair represents single {@link CompositeData row} of {@link TabularData}.<br />
     * The requirements are:<ul>
     * <li>{@link TabularType#getRowType()} defines {@code key} and {@code value} items of type
     * {@link SimpleType#STRING} (or convertible to String)</li>
     * <li>{@link TabularType#getIndexNames()} should contain {@code key} only.</li>
     * </ul>
     * </p>
     *
     * <p><strong>#2.</strong> If any of the {@link TabularType#getIndexNames()} point to {@link CompositeType} fields of
     * type different than {@link SimpleType}, the converted {@link TabularData} is said to use <em>complex keys</em>.
     * This means that the <em>complex values</em> can't be used as keys of a {@link JSONObject}.<br />
     * In this case, each {@link TabularData#get row} of the {@link TabularData} is converted to single
     * {@link JSONObject} with keys being String item names of {@link CompositeData} and values being complex
     * item values of {@link CompositeData}.<br />
     * In order to recreate such representation, index keys are stored under {@code indexNames} field of the returned
     * {@link JSONObject} and rows are returned in {@link JSONArray} under {@code values} field of the returned
     * {@link JSONObject}.</p>
     *
     * <p><strong>#3.</strong> If all of the index items are {@link SimpleType}, we can convert the index
     * <em>values</em> to Strings and use them as keys of the returned {@link JSONObject}. We can avoid then the
     * extra {@code indexNames} and {@code values} top level fields and serialize the {@link TabularData} as
     * multi-layered {@link JSONObject} where leafs are map representations of {@link CompositeData rows} - with keys
     * being item names of {@link CompositeData}. The more {@link TabularType#getIndexNames() index names} the
     * more nested the leafs are stored in the returned {@link JSONObject}.</p>
     *
     * <p>Let's consider a {@link CompositeType} with 3 items:<ul>
     * <li>{@code id} of {@link SimpleType#INTEGER} type</li>
     * <li>{@code name} of {@link SimpleType#STRING} type</li>
     * <li>{@code attributes} of {@link CompositeType} type - a nested map.</li>
     * </ul>
     * A JSON representation of this {@link CompositeType} could look like this:<pre>
     * {@code
     * {
     *     "id": 123,
     *     "name": "some value",
     *     "attributes": {
     *         "a1": "v1",
     *         "a2": "v2"
     *     }
     * }
     * }
     * </pre>
     * We can define two different {@link TabularType tabular types} based on this {@link CompositeType}:<ul>
     * <li>One with index keys: {@code id} and {@code name}</li>
     * <li>One with index keys: {@code id} and {@code attributes} - this would be a valid
     * {@link TabularType} with complex keys.</li>
     * </ul>
     * </p>
     *
     * <p>Now, when the {@link TabularType#getIndexNames()} are all {@link SimpleType simple types} we can get
     * this nested map representation <strong>#3</strong> (notice the duplication of data):<pre>
     * {@code
     * {
     *     "123": {
     *         "some value": {
     *             "id": 123,
     *             "name": "some value",
     *             "attributes": {
     *                 "a1": "v1",
     *                 "a2": "v2"
     *             }
     *         }
     *     }
     * }
     * }
     * </pre></p>
     *
     * <p>When the {@link TabularType#getIndexNames()} use also complex types, we'll get this representation
     * <strong>#2</strong>:
     * <pre>
     * {@code
     * {
     *     "indexNames": [ "id", "attributes" ],
     *     "values": [
     *         {
     *             "id": 123,
     *             "name": "some value",
     *             "attributes": {
     *                 "a1": "v1",
     *                 "a2": "v2"
     *             }
     *         }
     *     ]
     * }
     * }
     * </pre>
     * </p>
     *
     * <p>Accessing {@link TabularData} with a {@code path} is only supported for index keys of {@link SimpleType},
     * i.e. each index name must point to a String-convertible value.
     * As many path elements must be provided as index names for the tabular type exists
     * (i.e. {@code pPathParts.size() >= pTabularData.getTabularType().getIndexNames().size()})</p>
     *
     * <p>For {@link TabularData} of type <strong>#1</strong>, conforming to {@link javax.management.MXBean}
     * specification, a path access with the single {@code key} value will return the content of the {@code value}
     * item. For non-{@link javax.management.MXBean} {@link TabularType tabular types}, w need proper path
     * specification.</p>
     *
     * @param pConverter   the global converter in order to be able do dispatch for
     *                     serializing inner data types
     * @param pTabularData the value to convert
     * @param pPathParts   extra arguments which contain e.g. a path
     * @param pJsonify     whether to convert to a JSON object/list or whether the plain object
     *                     should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException
     */
    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pTabularData, Deque<String> pPathParts, boolean pJsonify)
                throws AttributeNotFoundException {
        TabularData td = (TabularData) pTabularData;

        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            try {
                pPathParts.push(pathPart); // Need it later on for the index
                CompositeData cd = extractCompositeDataFromPath(pConverter, td, pPathParts);
                return pConverter.extractObject(
                    cd != null && TabularDataConverter.isMXBeanMapWithSimpleKeys(td.getTabularType()) ? cd.get("value") : cd,
                    pPathParts, pJsonify);
            } catch (AttributeNotFoundException exp) {
                ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
                return faultHandler.handleException(exp);
            }
        } else {
            if (pJsonify) {
                return TabularDataConverter.isMXBeanMapWithSimpleKeys(td.getTabularType()) ?
                    convertMXBeanTabularDataToJSON(pConverter, td, pPathParts) :
                    convertGenericTabularDataToJSON(pConverter, td, pPathParts);
            } else {
                return td;
            }
        }
    }

    @Override
    public boolean canSetValue() {
        return false;
    }

    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("TabularData cannot be written to");
    }

    /**
     * <p>When {@link TabularType} defines an {@link javax.management.MXBean}-compatible representation of
     * {@link java.util.Map} with {@code key} and {@code value} items in {@link TabularType#getRowType()} and with
     * single {@code key} index item, we may return specific representation of {@link TabularData}.</p>
     *
     * <p>The returned {@link JSONObject} has keys from {@code key} items and values from {@code value} items of
     * all rows from {@link TabularData}. Key uniqueness ensures that the size of the map is equal to the number
     * of rows of {@link TabularData}. This matches method #1 specified in {@link TabularDataConverter}.</p>
     *
     * @param pConverter
     * @param pTabularData
     * @param pPath
     * @return
     * @throws AttributeNotFoundException
     */
    private Object convertMXBeanTabularDataToJSON(ObjectToJsonConverter pConverter, TabularData pTabularData, Deque<String> pPath)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (Object rowObject : pTabularData.values()) {
            CompositeData row = (CompositeData) rowObject;
            Deque<String> path = new LinkedList<>(pPath);
            Object keyObject = row.get("key");
            if (keyObject != null) {
                try {
                    String stringKey = (String) pConverter.getConverter().convert(String.class.getName(), keyObject);
                    Object value = pConverter.extractObject(row.get("value"), path, true);
                    ret.put(stringKey, value);
                } catch (ValueFaultHandler.AttributeFilteredException exp) {
                    // Skip to next object since attribute was filtered
                }
            }
        }
        if (!pTabularData.isEmpty() && ret.isEmpty()) {
            // Bubble up if not a single thingy has been found
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /**
     * <p>When {@link TabularType} doesn't match {@link javax.management.MXBean} compatible map, we have to construct
     * more complex {@link JSONObject}.</p>
     *
     * @param pConverter
     * @param pTabularData
     * @param pPath
     * @return
     * @throws AttributeNotFoundException
     */
    private Object convertGenericTabularDataToJSON(ObjectToJsonConverter pConverter, TabularData pTabularData, Deque<String> pPath)
            throws AttributeNotFoundException {
        TabularType type = pTabularData.getTabularType();
        if (hasComplexKeys(type)) {
            return convertTabularDataWithComplexKeys(pConverter, pTabularData, pPath);
        } else {
            return convertTabularDataWithSimpleKeys(pConverter, pTabularData, pPath);
        }
    }

    /**
     * Check whether the {@link TabularType} uses complex keys (of types other than {@link SimpleType}.
     *
     * @param pType
     * @return
     */
    private boolean hasComplexKeys(TabularType pType) {
        List<String> indexes = pType.getIndexNames();
        CompositeType rowType = pType.getRowType();
        for (String index : indexes) {
            if (!(rowType.getType(index) instanceof SimpleType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert {@link TabularType} with complex keys into {@link JSONObject} of kind <strong>#2</strong>.
     *
     * @param pConverter
     * @param pTabularData
     * @param pPath
     * @return
     * @throws AttributeNotFoundException
     */
    @SuppressWarnings("unchecked")
    private Object convertTabularDataWithComplexKeys(ObjectToJsonConverter pConverter, TabularData pTabularData, Deque<String> pPath)
            throws AttributeNotFoundException {
        if (!pPath.isEmpty()) {
            throw new IllegalArgumentException("Cannot use a path for converting tabular data with complex keys (" +
                pTabularData.getTabularType().getRowType() + ")");
        }
        JSONObject ret = new JSONObject();

        // array of index names as JSONArray of Strings
        TabularType type = pTabularData.getTabularType();
        JSONArray indexNames = new JSONArray(type.getIndexNames().size());
        indexNames.addAll(type.getIndexNames());
        ret.put("indexNames", indexNames);

        // array of rows mapped using CompositeDataAccessor
        JSONArray values = new JSONArray(pTabularData.values().size());
        // Here no special handling for wildcard paths since paths are not supported for this use case (yet)
        for (CompositeData row : (Collection<CompositeData>) pTabularData.values()) {
            values.add(pConverter.extractObject(row, pPath, true));
        }
        ret.put("values", values);

        return ret;
    }

    /**
     * Convert {@link TabularType} with simple keys into {@link JSONObject} of kind <strong>#3</strong>.
     *
     * @param pConverter
     * @param pTabularData
     * @param pPath
     * @return
     * @throws AttributeNotFoundException
     */
    @SuppressWarnings("unchecked")
    private Object convertTabularDataWithSimpleKeys(ObjectToJsonConverter pConverter, TabularData pTabularData, Deque<String> pPath)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        TabularType type = pTabularData.getTabularType();
        List<String> indexNames = type.getIndexNames();

        boolean found = false;
        for (CompositeData tableRow : (Collection<CompositeData>) pTabularData.values()) {
            Deque<String> path = new LinkedList<>(pPath);
            try {
                JSONObject targetJSONObject = ret;
                // each index nests the actual map representation of the row
                for (int i = 0; i < indexNames.size() - 1; i++) {
                    Object indexValue = pConverter.extractObject(tableRow.get(indexNames.get(i)), null, true);
                    String index = (String) pConverter.getConverter().convert(String.class.getName(), indexValue);
                    targetJSONObject = getNextMap(targetJSONObject, indexValue == null ? null : index);
                }
                // and finally convert entire row
                Object row = pConverter.extractObject(tableRow, path, true);
                String finalIndex = indexNames.get(indexNames.size() - 1);
                Object finalIndexValue = pConverter.extractObject(tableRow.get(finalIndex), null, true);
                String index = (String) pConverter.getConverter().convert(String.class.getName(), finalIndexValue);
                targetJSONObject.put(finalIndexValue == null ? null : index, row);
                found = true;
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Ignoring filtered attributes
            }
        }
        if (!pTabularData.isEmpty() && !found) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    private JSONObject getNextMap(JSONObject pJsonObject, String pKey) {
        JSONObject ret = (JSONObject) pJsonObject.get(pKey);
        if (ret == null) {
            ret = new JSONObject();
            pJsonObject.put(pKey, ret);
        }
        return ret;
    }

    private CompositeData extractCompositeDataFromPath(ObjectToJsonConverter pConverter, TabularData pTd, Deque<String> pPath)
            throws AttributeNotFoundException {
        // We first try it as a key
        TabularType type = pTd.getTabularType();
        List<String> indexNames = type.getIndexNames();
        validatePathWithDeclaredIndex(pPath, indexNames);

        Object[] keys = new Object[indexNames.size()];
        CompositeType rowType = type.getRowType();
        List<String> pathPartsUsed = new ArrayList<>();
        for (int i = 0; i < indexNames.size(); i++) {
            String path = pPath.pop();
            pathPartsUsed.add(path);
            keys[i] = getKey(pConverter, rowType, indexNames.get(i), path);
        }
        if (pTd.containsKey(keys)) {
            return pTd.get(keys);
        } else {
            throw new AttributeNotFoundException("No entry for index " + pathPartsUsed + " found");
        }
    }

    private void validatePathWithDeclaredIndex(Deque<String> pPathStack, List<String> pIndexNames)
            throws AttributeNotFoundException {
        if (pIndexNames.size() > pPathStack.size()) {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < pIndexNames.size(); i++) {
                buf.append(pIndexNames.get(i));
                if (i < pIndexNames.size() - 1) {
                    buf.append(",");
                }
            }
            throw new AttributeNotFoundException("No enough keys on path stack provided for accessing tabular data with index names " + buf);
        }
    }

    private Object getKey(ObjectToJsonConverter pConverter, CompositeType rowType, String key, String value)  {
        OpenType<?> keyType = rowType.getType(key);
        if (keyType instanceof SimpleType) {
            return objectToOpenTypeConverter.convert(keyType, value);
        }

        throw new IllegalArgumentException("All keys must be of SimpleType for accessing TabularData via a path. " +
                                           "This is not the case for '" + key + "' which is of type " + keyType);
    }

}
