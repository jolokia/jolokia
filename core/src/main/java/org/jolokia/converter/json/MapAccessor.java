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
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.Converter;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONObject;

/**
 * {@link org.jolokia.converter.json.ObjectAccessor} for {@link Map maps}.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class MapAccessor implements org.jolokia.converter.json.ObjectAccessor {

    private static final int MAX_STRING_LENGTH = 400;

    @Override
    public Class<?> getType() {
        return Map.class;
    }

    /**
     * Convert a Map to {@link JSONObject} (if {@code pJsonify} is {@code true}). If a path is used, the
     * first element of the path is interpreted as a key into the map and the extracted object is converted
     * value under that key. If the first element is {@code null}, new map is returned, possibly with values
     * extracted from existing values using the remainder of the path stack.
     *
     * @param pConverter the global converter to convert inner values and get serialization options
     * @param pMap       the value to convert (must be a {@link Map})
     * @param pPathParts if not empty, top value may be an index into the list and remaining parts are passed recursively
     * @param pJsonify   whether to convert the map into a {@link JSONObject} or its element to a relevant JSON representation
     * @return the extracted object or entire map when the {@code pPathParts} stack is empty
     * @throws AttributeNotFoundException
     */
    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pMap, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        // the map is not necessarily using string keys
        // see https://github.com/jolokia/jolokia/issues/732
        Map<?, ?> map = (Map<?, ?>) pMap;
        int length = pConverter.getCollectionLength(map.size());
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            return extractMapValue(pConverter, map, pPathParts, pJsonify, pathPart);
        } else {
            return pJsonify ? mapToJSON(pConverter, map, pPathParts, length) : map;
        }
    }

    @Override
    public boolean canSetValue() {
        return true;
    }

    /**
     * Set the value within a map, where the attribute is taken as key into the map.
     *
     * @param pConverter the global converter to convert the value being set into a class of the map value
     * @param pMap       a {@link Map} to set the value into
     * @param pKey       key in the map where to put the value, which is tricky, as the map keys may not be strings
     * @param pValue     the new value to set, subject to conversion
     * @return the old value or {@code null} if a new map entry was created
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pMap, String pKey, Object pValue) {
        if (!(pMap instanceof Map)) {
            throw new IllegalArgumentException("MapAccessor can't access objects of type " + pMap.getClass());
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) pMap;
        Object newKey = pKey;
        Object oldKey = null;

        // we can't quickly convert a pKey to a proper class, because we can't tell what is the class of the key
        // so the best effort is to check the class of first found key and attempt conversion
        if (!map.isEmpty()) {
            Object someKey = map.keySet().iterator().next();
            newKey = pConverter.convert(someKey.getClass().getName(), newKey);
        }
        // for empty maps we treat the map as keyed by Strings...
        // previously we were iterating all the keys and comparing oldKey.toString() with newKey

        Object oldValue = ((Map<?, ?>) pMap).get(newKey);
        Object newValue = pValue;
        if (oldValue != null) {
            // best effort conversion into a class of previous value, otherwise, let's keep existing class
            newValue = pConverter.convert(oldValue.getClass().getName(), pValue);
        }
        map.put(newKey, newValue);

        return oldValue;
    }

    /**
     * Serialize given {@link Map} into a {@link JSONObject} recursively, limited by
     * {@link org.jolokia.core.service.serializer.SerializeOptions}
     *
     * @param pConverter
     * @param pMap
     * @param pPath
     * @param pLength
     * @return
     * @throws AttributeNotFoundException
     */
    private JSONObject mapToJSON(ObjectToJsonConverter pConverter, Map<?, ?> pMap, Deque<String> pPath, int pLength)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        int i = 0;
        for (Map.Entry<?, ?> entry : pMap.entrySet()) {
            Deque<String> paths = new LinkedList<>(pPath);
            try {
                // we need to convert the key to a String in a better way than .toString()
                //  - key is explicitly converted into String, skipping the "extraction", because we don't
                //    want simplifiers to kick in (for example turning File into a map of properties like length
                //    and canonical name). This means that some keys are explicitly forbidden - for example
                //    Map<Long[], String> will not work with Jolokia, unless when being registered
                //    as MXBean - we'd get TabularType with different behavior
                //  - value is extracted as JSON value
                ret.put((String) pConverter.getConverter().convert(String.class.getName(), entry.getKey()),
                    pConverter.extractObject(entry.getValue(), paths, true));
                if (++i >= pLength) {
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

    /**
     * Extract single element of a map
     *
     * @param pConverter
     * @param pMap       the map to get a value from
     * @param pPath      remaining path parts passed recursively when converting value of a map
     * @param pJsonify
     * @param pKey  String value of map key - should be converted to a proper key class
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractMapValue(ObjectToJsonConverter pConverter, Map<?, ?> pMap, Deque<String> pPath, boolean pJsonify, String pKey)
            throws AttributeNotFoundException {
        Object key = pKey;
        if (!pMap.isEmpty()) {
            Object someKey = pMap.keySet().iterator().next();
            key = pConverter.getConverter().convert(someKey.getClass().getName(), key);
        }

        if (pMap.containsKey(key)) {
            return pConverter.extractObject(pMap.get(key), pPath, pJsonify);
        }

        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        return faultHandler.handleException(
            new AttributeNotFoundException("Map key '" + pKey +
                "' is unknown for map " + trimString(pMap.toString())));
    }

    private String trimString(String pString) {
        if (pString.length() > MAX_STRING_LENGTH) {
            return pString.substring(0, MAX_STRING_LENGTH) + " ...";
        } else {
            return pString;
        }
    }

}
