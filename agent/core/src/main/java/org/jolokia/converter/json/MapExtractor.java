package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

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
 * Extractor for Maps (which turns {@link Map} into {@link JSONObject})
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class MapExtractor implements Extractor {
    private static final int MAX_STRING_LENGTH = 400;

    /** {@inheritDoc} */
    public Class getType() {
        return Map.class;
    }

    /**
     * Convert a Map to JSON (if <code>jsonify</code> is <code>true</code>). If a path is used, the
     * path is interpreted as a key into the map. The key in the path is a string and is compared agains
     * all keys in the map against their string representation.
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert which must be a {@link Map}
     * @param pPathParts extra argument stack which on top must be a key into the map
     * @param jsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                                Stack<String> pPathParts,boolean jsonify) throws AttributeNotFoundException {
        Map<Object,Object> map = (Map<Object,Object>) pValue;
        int length = pConverter.getCollectionLength(map.size());
        String pathParth = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathParth != null) {
            return extractMapValueWithPath(pConverter, pValue, pPathParts, jsonify, map, pathParth);
        } else {
            return jsonify ? extractMapValues(pConverter, pPathParts, jsonify, map, length) : map;
        }
    }

    private JSONObject extractMapValues(ObjectToJsonConverter pConverter, Stack<String> pPathParts, boolean jsonify, Map<Object, Object> pMap, int pLength) throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        int i = 0;
        for(Map.Entry entry : pMap.entrySet()) {
            Stack<String> paths = (Stack<String>) pPathParts.clone();
            try {
                ret.put(entry.getKey(),
                        pConverter.extractObject(entry.getValue(), paths, jsonify));
                if (++i > pLength) {
                    break;
                }
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Filtered out ...
            }
        }
        if (ret.isEmpty() && pLength > 0) {
            // Not a single value passed the filter
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    private Object extractMapValueWithPath(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathParts, boolean jsonify, Map<Object, Object> pMap, String pPathParth) throws AttributeNotFoundException {
        for (Map.Entry entry : pMap.entrySet()) {
            // We dont access the map via a lookup since the key
            // are potentially object but we have to deal with string
            // representations
            if(pPathParth.equals(entry.getKey().toString())) {
                return pConverter.extractObject(entry.getValue(), pPathParts, jsonify);
            }
        }
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        return faultHandler.handleException(
                new AttributeNotFoundException("Map key '" + pPathParth +
                                             "' is unknown for map " + trimString(pValue.toString())));
    }

    /**
     * Set the value within a map, where the attribute is taken as key into the map.
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pMap map on which to set the value
     * @param pKey key in the map where to put the value
     * @param pValue the new value to set
     * @return the old value or <code>null</code> if a new map entry was created/
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pMap, String pKey, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        Map<Object,Object> map = (Map<Object,Object>) pMap;
        Object oldValue = null;
        Object oldKey = pKey;
        for (Map.Entry entry : map.entrySet()) {
            // We dont access the map via a lookup since the key
            // are potentially object but we have to deal with string
            // representations
            if(pKey.equals(entry.getKey().toString())) {
                oldValue = entry.getValue();
                oldKey = entry.getKey();
                break;
            }
        }
        Object value =
                oldValue != null ?
                        pConverter.prepareValue(oldValue.getClass().getName(), pValue) :
                        pValue;
        map.put(oldKey,value);
        return oldValue;
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }

    private String trimString(String pString) {
        if (pString.length() > MAX_STRING_LENGTH) {
            return pString.substring(0, MAX_STRING_LENGTH) + " ...";
        } else {
            return pString;
        }
    }
}