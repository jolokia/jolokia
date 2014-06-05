package org.jolokia.converter.json;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONArray;

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
 * Extractor for extracting arrays of any kind.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class ArrayExtractor implements Extractor {

    /** {@inheritDoc} */
    public Class getType() {
        // Special handler, no specific Type
        return null;
    }

    /**
     * Extract an array and, if to be jsonified, put it into an {@link JSONArray}. An index can be used (on top of
     * the extra args stack) in order to specify a single value within the array.
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert (must be an aary)
     * @param pPathParts extra arguments stack, which is popped to get an index for extracting a single element
     *                   of the array
     * @param jsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException
     * @throws IndexOutOfBoundsException if an index is used which points outside the given list
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathParts,boolean jsonify) throws AttributeNotFoundException {
        int length = pConverter.getCollectionLength(Array.getLength(pValue));
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            return extractWithPath(pConverter, pValue, pPathParts, jsonify, pathPart);
        } else {
            return jsonify ? extractArray(pConverter, pValue, pPathParts, jsonify, length) : pValue;
        }
    }

    /**
     * Set a value in an array
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pInner object on which to set the value (which must be a {@link List})
     * @param pIndex index (as string) where to set the value within the array
     * @param pValue the new value to set

     * @return the old value at this index
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pIndex, Object  pValue)
            throws IllegalAccessException, InvocationTargetException {
        Class clazz = pInner.getClass();
        if (!clazz.isArray()) {
            throw new IllegalArgumentException("Not an array to set a value, but " + clazz +
                    ". (index = " + pIndex + ", value = " +  pValue + ")");
        }
        int idx;
        try {
            idx = Integer.parseInt(pIndex);
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Non-numeric index for accessing array " + pInner +
                    ". (index = " + pIndex + ", value to set = " +  pValue + ")",exp);
        }
        Class type = clazz.getComponentType();
        Object value = pConverter.prepareValue(type.getName(), pValue);
        Object oldValue = Array.get(pInner, idx);
        Array.set(pInner, idx, value);
        return oldValue;
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }

    private List<Object> extractArray(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPath, boolean jsonify, int pLength) throws AttributeNotFoundException {
        List<Object> ret = new JSONArray();
        for (int i = 0; i < pLength; i++) {
            Stack<String> path = (Stack<String>) pPath.clone();
            try {
                Object obj = Array.get(pValue, i);
                ret.add(pConverter.extractObject(obj, path, jsonify));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Filtered ...
            }
        }
        if (ret.isEmpty() && pLength > 0) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    private Object extractWithPath(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPath, boolean jsonify, String pPathPart) throws AttributeNotFoundException {
        try {
            Object obj = Array.get(pValue, Integer.parseInt(pPathPart));
            return pConverter.extractObject(obj, pPath, jsonify);
        } catch (NumberFormatException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is not numeric for accessing array"));
        } catch (ArrayIndexOutOfBoundsException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is out-of-bound for array of size " + Array.getLength(pValue)));
        }
    }


}
