package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.*;

import org.jolokia.converter.object.StringToObjectConverter;
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
     *  dependening on whether it represets a map for an {@link javax.management.MXBean} or is a regular data.
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
     *  For multi valued keys (i.e. {@link TabularType#getIndexNames()} is a list with more than one element), the
     *  returned JSON structure looks like (index names here are "key" and "innerkey")
     *  <pre>
     *      {
     *         "mykey1" : {
     *                       "myinner1" => { "key" : "mkey1", "innerkey" : "myinner1", "item" : "value1", .... }
     *                       "myinner2" => { "key" : "mkey1", "innerkey" : "myinner2", "item" : "value1", .... }
     *                       ....
     *                     }
     *         "mykey2" : {
     *                       "second1" => { "key" : "mkey2", "innerkey" : "second1", "item" : "value1", .... }
     *                       "second2" => { "key" : "mkey2", "innerkey" : "second2", "item" : "value1", .... }
     *                       ....
     *                    }
     *         ....
     *      }
     *  </pre>
     * </p>
     * <p>
     *   Accessing {@link TabularData} with a path is only supported for string keys, i.e. each index name must point
     *   to a string value. As many path elements must be provided as index names for the tabular type exists
     *   (i.e. <code>pExtraArgs.size() >= pValue.getTabularType().getIndexNames().size()</code>)
     *
     *   For TabularData representing maps, a path access with the single "key" value will
     *   return the content of the "value" value. For all other TabularData, the complete row to which the path points
     *   is returned.
     * </p>
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert
     * @param pExtraArgs extra arguments which contain e.g. a path
     * @param pJsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                         Stack<String> pExtraArgs,boolean pJsonify) throws AttributeNotFoundException {
        TabularData td = (TabularData) pValue;
        if (!pExtraArgs.isEmpty()) {
            CompositeData cd = extractCompositeDataFromPath(td, pExtraArgs);
            return pConverter.extractObject(
                            cd != null && checkForMxBeanMap(td.getTabularType()) ? cd.get("value") : cd,
                            pExtraArgs, pJsonify);
        } else {
            if (pJsonify) {
                return checkForMxBeanMap(td.getTabularType()) ?
                        convertMxBeanMapToJson(td,pExtraArgs,pConverter) :
                        convertTabularDataToJson(td, pExtraArgs, pConverter);
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
        return rowType.containsKey("key") && rowType.containsKey("value") && rowType.keySet().size() == 2;
    }

    private Object convertTabularDataToJson(TabularData pTd, Stack<String> pExtraArgs, ObjectToJsonConverter pConverter)
            throws AttributeNotFoundException {
        TabularType type = pTd.getTabularType();
        List<String> indexNames = type.getIndexNames();
        JSONObject ret = new JSONObject();
        for (CompositeData cd : (Collection <CompositeData>) pTd.values()) {
            JSONObject targetJSONObject = ret;
            for (int i = 0; i < indexNames.size() - 1; i++) {
                Object indexValue = pConverter.extractObject(cd.get(indexNames.get(i)), null, true);
                targetJSONObject = getNextMap(targetJSONObject,indexValue);
            }
            Object row = pConverter.extractObject(cd, pExtraArgs, true);
            String finalIndex = indexNames.get(indexNames.size() - 1);
            Object finalIndexValue = pConverter.extractObject(cd.get(finalIndex), null, true);
            targetJSONObject.put(finalIndexValue,row);
        }
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

    private CompositeData extractCompositeDataFromPath(TabularData pTd, Stack<String> pPathStack) {
        // We first try it as a key
        TabularType type = pTd.getTabularType();
        List<String> indexNames = type.getIndexNames();
        if (indexNames.size() > pPathStack.size()) {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < indexNames.size(); i++) {
                buf.append(indexNames.get(i));
                if (i < indexNames.size() - 1) {
                    buf.append(",");
                }
            }
            throw new IllegalArgumentException("No enough keys on path stack provided for accessing tabular data with index names "
                                               + buf.toString());
        }
        Object keys[] = new Object[indexNames.size()];
        CompositeType rowType = type.getRowType();
        for (int i = 0; i < indexNames.size(); i++) {
            validateRowType(rowType, indexNames.get(i));
            keys[i] = pPathStack.pop();
        }
        return pTd.get(keys);
    }

    // Maybe even convert to proper types. For now it is assumed,
    // that every key is of type string and an exception is thrown
    // if this is not the case.
    private void validateRowType(CompositeType rowType, String key) {
        OpenType keyType = rowType.getType(key);
        if (SimpleType.STRING != keyType) {
            throw new IllegalArgumentException("All keys must be a string type for accessing TabularData via a path. " +
                                               "This is not the case for '"
                                               + key + "' which is of type " + keyType);
        }
    }

    private Object convertMxBeanMapToJson(TabularData pTd, Stack<String> pExtraArgs, ObjectToJsonConverter pConverter)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (Object rowObject : pTd.values()) {
            CompositeData row = (CompositeData) rowObject;
            Object keyObject = row.get("key");
            if (keyObject != null) {
                Object value = pConverter.extractObject(row.get("value"),pExtraArgs,true);
                ret.put(keyObject.toString(),value);
            }
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