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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.LinkedList;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.Converter;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONArray;

/**
 * {@link org.jolokia.converter.json.ObjectAccessor} for arrays of any type (primitive or object arrays).
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class ArrayAccessor implements org.jolokia.converter.json.ObjectAccessor {

    @Override
    public Class<?> getType() {
        // Special accessor with no specific Type. This accessor should be used directly, not generically
        throw new UnsupportedOperationException("ArrayAccessor should be used directly");
    }

    /**
     * Extract an array or indexed element of an array. When {@code pJsonify}, return a {@link JSONArray} for
     * entire array or JSON representation of the value under some index.
     *
     * @param pConverter the global converter to convert inner values and get serialization options
     * @param pArray     the value to convert (must be an array)
     * @param pPathParts if not empty, top value may be an index into the array and remaining parts are passed recursively
     * @param pJsonify   whether to convert the array into {@link JSONArray} or its element to a relevant JSON representation
     * @return the extracted object or entire array when the {@code pPathParts} stack is empty
     * @throws AttributeNotFoundException if the index is not specified as non-negative integer
     * @throws IndexOutOfBoundsException  if the index is not within the array
     */
    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pArray, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        int length = pConverter.getCollectionLength(Array.getLength(pArray));
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            return extractArrayElement(pConverter, pArray, pPathParts, pJsonify, pathPart);
        } else {
            return pJsonify ? arrayToJSON(pConverter, pArray, pPathParts, length) : pArray;
        }
    }

    @Override
    public boolean canSetValue() {
        return true;
    }

    /**
     * Set a value in an array under given index
     *
     * @param pConverter the global converter to convert the value being set into {@link Class#getComponentType()}
     * @param pArray     an array to set the value into
     * @param pIndex     index (as string) where to set the value within the array
     * @param pValue     the new value to set, subject to conversion
     * @return the old value at this index
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pArray, String pIndex, Object pValue) {
        Class<?> clazz = pArray.getClass();
        if (!clazz.isArray()) {
            throw new IllegalArgumentException("Not an array to set a value, but " + clazz +
                ". (index = " + pIndex + ", value = " + pValue + ")");
        }
        int idx;
        try {
            idx = Integer.parseInt(pIndex);
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Non-numeric index for accessing array " + pArray +
                ". (index = " + pIndex + ", value to set = " + pValue + ")", exp);
        }
        Class<?> type = clazz.getComponentType();
        Object value = pConverter.convert(type.getName(), pValue);
        Object oldValue = Array.get(pArray, idx);
        Array.set(pArray, idx, value);

        return oldValue;
    }

    /**
     * Serialize given array into a {@link JSONArray} recursively, limited by
     * {@link org.jolokia.core.service.serializer.SerializeOptions}
     *
     * @param pConverter
     * @param pArray
     * @param pPath
     * @param pLength
     * @return
     * @throws AttributeNotFoundException
     */
    private JSONArray arrayToJSON(ObjectToJsonConverter pConverter, Object pArray, Deque<String> pPath, int pLength)
            throws AttributeNotFoundException {
        JSONArray ret = new JSONArray(pLength);
        for (int i = 0; i < pLength; i++) {
            // a copy passed (and drained) for each element
            Deque<String> path = new LinkedList<>(pPath);
            try {
                Object obj = Array.get(pArray, i);
                ret.add(pConverter.extractObject(obj, path, true));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Filtered, because an array element may not contain given attribute...
            }
        }
        if (ret.isEmpty() && pLength > 0) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /**
     * Extract single element of an array
     *
     * @param pConverter
     * @param pValue     the array to get an element from
     * @param pPath      remaining path parts passed recursively when converting element of an array
     * @param pJsonify
     * @param pPathPart  String value of array index - should be parsable to int
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractArrayElement(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPath, boolean pJsonify, String pPathPart)
            throws AttributeNotFoundException {
        try {
            Object obj = Array.get(pValue, Integer.parseInt(pPathPart));
            return pConverter.extractObject(obj, pPath, pJsonify);
        } catch (NumberFormatException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is not a numeric for accessing an array"));
        } catch (ArrayIndexOutOfBoundsException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is out-of-bound for an array of size " + Array.getLength(pValue)));
        }
    }

}
