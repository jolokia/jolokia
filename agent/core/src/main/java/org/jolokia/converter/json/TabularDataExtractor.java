package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;
import javax.management.openmbean.*;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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


/**
 * @author roland
 * @since Apr 19, 2009
 */
public class TabularDataExtractor implements Extractor {

    /** {@inheritDoc} */
    public Class getType() {
        return TabularData.class;
    }

    /**
     * <p>
     *  Extract a {@link TabularData}. The JSON representation of a tabular data is different,
     *  depending on whether it represents a map for an {@link javax.management.MXBean} or is a regular data.
     * </p>
     * <p>
     *  I.e. for an tabular data which have a row type with two column "key" and "value", then
     *  a map is returned (with the "key" values as map keys and "value" values as map values).
     * </p>
     * <p>
     *  Otherwise a map of (one or more) maps is returned, where the map keys are taken
     *  from {@link TabularType} of the presented data. E.g. if there is a single valued key
     *  <code>"key"</code>, then the returned JSON looks like
     *  <pre>
     *      {
     *         "mykey1" : { "key" : "mkey1", "item" : "value1", .... }
     *         "mykey2" : { "key" : "mkey2", "item" : "value2", .... }
     *         ....
     *      }
     *  </pre>
     *  For multi valued keys of simple open types (i.e. {@link TabularType#getIndexNames()} is a list with more than one element), the
     *  returned JSON structure looks like (index names here are "key" and "innerkey")
     *  <pre>
     *      {
     *         "mykey1" : {
     *                       "myinner1" : { "key" : "mkey1", "innerkey" : "myinner1", "item" : "value1", .... }
     *                       "myinner2" : { "key" : "mkey1", "innerkey" : "myinner2", "item" : "value1", .... }
     *                       ....
     *                     }
     *         "mykey2" : {
     *                       "second1" : { "key" : "mkey2", "innerkey" : "second1", "item" : "value1", .... }
     *                       "second2" : { "key" : "mkey2", "innerkey" : "second2", "item" : "value1", .... }
     *                       ....
     *                    }
     *         ....
     *      }
     *  </pre>
     *  If keys are used, which themselves are complex objects (like composite data), this hierarchical map
     *  structure can not be used. In this case an object with two keys is returned: "indexNames" holds the
     *  name of the key index and "values" is an array of all rows which are represented as JSON objects:
     *  <pre>
     *      {
     *        "indexNames" : [ "key", "innerkey" ],
     *        "values" : [
     *           { "key" : "mykey1", "innerkey" : { "name" : "a", "number" : 4711 }, "item" : "value1", .... },
     *           { "key" : "mykey2", "innerkey" : { "name" : "b", "number" : 815 }, "item" : "value2", .... },
     *           ...
     *        ]
     *      }
     *  </pre>
     * </p>
     * <p>
     *   Accessing {@link TabularData} with a path is only supported for simple type keys, i.e. each index name must point
     *   to a string representation of a simple open type. As many path elements must be provided as index names for
     *   the tabular type exists (i.e. <code>pExtraArgs.size() >= pValue.getTabularType().getIndexNames().size()</code>)
     *
     *   For TabularData representing maps, a path access with the single "key" value will
     *   return the content of the "value" value. For all other TabularData, the complete row to which the path points
     *   is returned.
     * </p>
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert
     * @param pPathParts extra arguments which contain e.g. a path
     * @param pJsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                                Stack<String> pPathParts,boolean pJsonify) throws AttributeNotFoundException {
        TabularData td = (TabularData) pValue;
        String tdPath = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (tdPath != null) {
            try {
                pPathParts.push(tdPath); // Need it later on for the index
                CompositeData cd = extractCompositeDataFromPath(td, pPathParts);
                return pConverter.extractObject(
                        cd != null && checkForMxBeanMap(td.getTabularType()) ? cd.get("value") : cd,
                        pPathParts, pJsonify);
            } catch (AttributeNotFoundException exp) {
                ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
                return faultHandler.handleException(exp);
            }
        } else {
            if (pJsonify) {
                return checkForMxBeanMap(td.getTabularType()) ?
                        convertMxBeanMapToJson(td,pPathParts,pConverter) :
                        convertTabularDataToJson(td, pPathParts, pConverter);
            } else {
                return td;
            }
        }
    }

    // ====================================================================================================

    /**
     * Check whether the given tabular type represents a MXBean map. See the
     * {@link javax.management.MXBean} specification for
     * details how a map is converted to {@link TabularData} by the MXBean framework.
     *
     * @param pType type of tabular data to convert
     * @return true if this type represents an MXBean map, false otherwise.
     */
    private boolean checkForMxBeanMap(TabularType pType) {
        CompositeType rowType = pType.getRowType();
        return rowType.containsKey("key") && rowType.containsKey("value") && rowType.keySet().size() == 2
               // Only convert to map for simple types for all others use normal conversion. See #105 for details.
               && rowType.getType("key") instanceof  SimpleType;
    }

    private Object convertTabularDataToJson(TabularData pTd, Stack<String> pExtraArgs, ObjectToJsonConverter pConverter)
            throws AttributeNotFoundException {
        TabularType type = pTd.getTabularType();
        if (hasComplexKeys(type)) {
            return convertTabularDataDirectly(pTd, pExtraArgs, pConverter);
        } else {
            return convertToMaps(pTd, pExtraArgs, pConverter);
        }
    }

    // Check, whether all keys are simple types or not
    private boolean hasComplexKeys(TabularType pType) {
        List<String> indexes = pType.getIndexNames();
        CompositeType rowType = pType.getRowType();
        for (String index : indexes) {
            if ( ! (rowType.getType(index) instanceof SimpleType)) {
                return true;
            }
        }
        return false;
    }

    // Convert tabular data to (nested) maps. Path access is allowed here
    private Object convertToMaps(TabularData pTd, Stack<String> pExtraArgs, ObjectToJsonConverter pConverter) throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        TabularType type = pTd.getTabularType();
        List<String> indexNames = type.getIndexNames();

        boolean found = false;
        for (CompositeData cd : (Collection<CompositeData>) pTd.values()) {
            Stack<String> path = (Stack<String>) pExtraArgs.clone();
            try {
                JSONObject targetJSONObject = ret;
                // TODO: Check whether all keys can be represented as simple types. If not, well
                // we dont do any magic and return the tabular data as an array.
                for (int i = 0; i < indexNames.size() - 1; i++) {
                    Object indexValue = pConverter.extractObject(cd.get(indexNames.get(i)), null, true);
                    targetJSONObject = getNextMap(targetJSONObject, indexValue);
                }
                Object row = pConverter.extractObject(cd, path, true);
                String finalIndex = indexNames.get(indexNames.size() - 1);
                Object finalIndexValue = pConverter.extractObject(cd.get(finalIndex), null, true);
                targetJSONObject.put(finalIndexValue, row);
                found = true;
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Ignoring filtered attributes
            }
        }
        if (!pTd.isEmpty() && !found) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    // Convert to a direct representation of the tabular data
    private Object convertTabularDataDirectly(TabularData pTd, Stack<String> pExtraArgs, ObjectToJsonConverter pConverter)
            throws AttributeNotFoundException {
        if (!pExtraArgs.empty()) {
            throw new IllegalArgumentException("Cannot use a path for converting tabular data with complex keys (" +
                                               pTd.getTabularType().getRowType() + ")");
        }
        JSONObject ret = new JSONObject();
        JSONArray indexNames = new JSONArray();
        TabularType type = pTd.getTabularType();
        for (String index : type.getIndexNames()) {
            indexNames.add(index);
        }
        ret.put("indexNames",indexNames);

        JSONArray values = new JSONArray();
        // Here no special handling for wildcard pathes since pathes are not supported for this use case (yet)
        for (CompositeData cd : (Collection<CompositeData>) pTd.values()) {
            values.add(pConverter.extractObject(cd, pExtraArgs, true));
        }
        ret.put("values",values);

        return ret;
    }

    private JSONObject getNextMap(JSONObject pJsonObject, Object pKey) {
        JSONObject ret = (JSONObject) pJsonObject.get(pKey);
        if (ret == null) {
            ret = new JSONObject();
            pJsonObject.put(pKey, ret);
        }
        return ret;
    }

    private CompositeData extractCompositeDataFromPath(TabularData pTd, Stack<String> pPathStack)
            throws AttributeNotFoundException {
        // We first try it as a key
        TabularType type = pTd.getTabularType();
        List<String> indexNames = type.getIndexNames();
        checkPathFitsIndexNames(pPathStack, indexNames);

        Object keys[] = new Object[indexNames.size()];
        CompositeType rowType = type.getRowType();
        List<String> pathPartsUsed = new ArrayList<String>();
        for (int i = 0; i < indexNames.size(); i++) {
            String path = pPathStack.pop();
            pathPartsUsed.add(path);
            keys[i] = getKey(rowType, indexNames.get(i), path);
        }
        if (pTd.containsKey(keys)) {
            return pTd.get(keys);
        } else {
            throw new AttributeNotFoundException("No entry with " + pathPartsUsed + " found");
        }
    }

    private void checkPathFitsIndexNames(Stack<String> pPathStack, List<String> pIndexNames) throws AttributeNotFoundException {
        if (pIndexNames.size() > pPathStack.size()) {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < pIndexNames.size(); i++) {
                buf.append(pIndexNames.get(i));
                if (i < pIndexNames.size() - 1) {
                    buf.append(",");
                }
            }
            throw new AttributeNotFoundException("No enough keys on path stack provided for accessing tabular data with index names "
                                                 + buf.toString());
        }
    }

    // The key is tried to convert to the proper type. These checks are
    // a bit redundant, since this sort of conversion is already offered
    // in StringToOpenTypeConverter. Unfortunately, this converter is not
    // easily available here. For 2.0 the modularity aspects are refactored
    // from the ground up, so I can live with the solution here.
    // See also #97 for details.
    private Object getKey(CompositeType rowType, String key, String value)  {
        OpenType keyType = rowType.getType(key);
        if (SimpleType.STRING == keyType) {
            return value;
        } else if (SimpleType.INTEGER == keyType) {
            return Integer.parseInt(value);
        } else if (SimpleType.LONG == keyType) {
            return Long.parseLong(value);
        } else if (SimpleType.SHORT == keyType) {
            return Short.parseShort(value);
        } else if (SimpleType.BYTE == keyType) {
            return Byte.parseByte(value);
        } else if (SimpleType.OBJECTNAME == keyType) {
            try {
                return new ObjectName(value);
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException("Can not convert " + value + " to an ObjectName",e);
            }
        } else {
            throw new IllegalArgumentException("All keys must be a string, integer, long, short, byte or ObjectName type for accessing TabularData via a path. " +
                                               "This is not the case for '"
                                               + key + "' which is of type " + keyType);
        }
    }

    private Object convertMxBeanMapToJson(TabularData pTd, Stack<String> pExtraArgs, ObjectToJsonConverter pConverter)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (Object rowObject : pTd.values()) {
            CompositeData row = (CompositeData) rowObject;
            Stack<String> path = (Stack<String>) pExtraArgs.clone();
            Object keyObject = row.get("key");
            if (keyObject != null) {
                try {
                    Object value = pConverter.extractObject(row.get("value"), path, true);
                    ret.put(keyObject.toString(), value);
                } catch (ValueFaultHandler.AttributeFilteredException exp) {
                    // Skip to next object since attribute was filtered
                }
            }
        }
        if (!pTd.isEmpty() && ret.isEmpty()) {
            // Bubble up if not a single thingy has been found
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /**
     * Throws always {@link IllegalArgumentException} since tabular data is immutable
     */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("TabularData cannot be written to");
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return false;
    }

}