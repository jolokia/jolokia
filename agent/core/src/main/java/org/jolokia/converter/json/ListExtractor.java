package org.jolokia.converter.json;

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
 * Extract a {@link List}
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class ListExtractor implements Extractor {

    /** {@inheritDoc} */
    public Class getType() {
        return List.class;
    }

    /**
     * Extract a list and, if to be jsonified, put it into an {@link JSONArray}. An index can be used (on top of
     * the extra args stack) in order to specify a single value within the list.
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert (must be a {@link List})
     * @param pPathParts extra arguments stack, which is popped to get an index for extracting a single element
     *                   of the list
     * @param jsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException
     * @throws IndexOutOfBoundsException if an index is used which points outside the given list
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathParts,boolean jsonify)
            throws AttributeNotFoundException {
        List list = (List) pValue;
        int length = pConverter.getCollectionLength(list.size());
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            return extractWithPath(pConverter, list, pPathParts, jsonify, pathPart);
        } else {
            return jsonify ? extractListAsJson(pConverter, list, pPathParts, length) : list;
        }
    }

    /**
     * Set a value within a list
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pInner object on which to set the value (which must be a {@link List})
     * @param pIndex index (as string) where to set the value within the list
     * @param pValue the new value to set

     * @return the old value at this index
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pIndex, Object  pValue)
            throws IllegalAccessException, InvocationTargetException {
        List list = (List) pInner;
        int idx;
        try {
            idx = Integer.parseInt(pIndex);
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Non-numeric index for accessing collection " + pInner +
                    ". (index = " + pIndex + ", value to set = " +  pValue + ")",exp);
        }

        // For a collection, we can infer the type within the collection. We are trying to fetch
        // the old value, and if set, we use its type. Otherwise, we simply use string as value.
        Object oldValue = list.get(idx);
        Object value =
                oldValue != null ?
                        pConverter.prepareValue(oldValue.getClass().getName(), pValue) :
                        pValue;
        list.set(idx,value);
        return oldValue;
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }

    private Object extractWithPath(ObjectToJsonConverter pConverter, List pList, Stack<String> pStack, boolean jsonify, String pPathPart) throws AttributeNotFoundException {
        try {
            int idx = Integer.parseInt(pPathPart);
            return pConverter.extractObject(pList.get(idx), pStack, jsonify);
        } catch (NumberFormatException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is not numeric for accessing list"));
        } catch (IndexOutOfBoundsException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is out-of-bound for a list of size " + pList.size()));
        }
    }

    private Object extractListAsJson(ObjectToJsonConverter pConverter, List pList, Stack<String> pPath, int pLength) throws AttributeNotFoundException {
        List ret = new JSONArray();
        for (int i = 0;i < pLength; i++) {
            Stack<String> path = (Stack<String>) pPath.clone();
            try {
                ret.add(pConverter.extractObject(pList.get(i), path, true));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // This element is filtered out, next one ...
            }
        }
        if (ret.isEmpty() && pLength > 0) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }


}