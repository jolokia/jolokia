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
package org.jolokia.service.serializer.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.service.serializer.object.Converter;
import org.jolokia.json.JSONArray;

/**
 * {@link ObjectAccessor} for {@link List lists}. Unlike the accessor for {@link java.util.Collection collections}
 * this one can set list elements.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class ListAccessor implements ObjectAccessor {

    @Override
    public Class<?> getType() {
        return List.class;
    }

    /**
     * Extract a list or indexed element of a list. When {@code pJsonify}, return a {@link JSONArray} for
     * entire list or JSON representation of the value under some index.
     *
     * @param pConverter the global converter to convert inner values and get serialization options
     * @param pList      the value to convert (must be a {@link List})
     * @param pPathParts if not empty, top value may be an index into the list and remaining parts are passed recursively
     * @param pJsonify   whether to convert the list into a {@link JSONArray} or its element to a relevant JSON representation
     * @return the extracted object or entire list when the {@code pPathParts} stack is empty
     * @throws AttributeNotFoundException if the index is not specified as non-negative integer
     * @throws IndexOutOfBoundsException  if an index is used which points outside the given list
     */
    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pList, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        List<?> list = (List<?>) pList;
        int length = pConverter.getCollectionLength(list.size());
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            return extractListItem(pConverter, list, pPathParts, pJsonify, pathPart);
        } else {
            return pJsonify ? listToJSON(pConverter, list, pPathParts, length) : list;
        }
    }

    @Override
    public boolean canSetValue() {
        return true;
    }

    /**
     * Set a value in a list under given index
     *
     * @param pConverter the global converter to convert the value being set into a class of the list element
     * @param pList      a {@link List} to set the value into
     * @param pIndex     index (as string) where to set the value within the list
     * @param pValue     the new value to set, subject to conversion
     * @return the old value at this index
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pList, String pIndex, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        if (!(pList instanceof List)) {
            throw new IllegalArgumentException("ListAccessor can't access objects of type " + pList.getClass());
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) pList;
        int idx;
        try {
            idx = Integer.parseInt(pIndex);
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Non-numeric index for accessing list " + pList +
                ". (index = " + pIndex + ", value to set = " + pValue + ")", exp);
        }

        // we can't really check by reflection whether the passed list is java.util.LinkedList<String> or
        // java.util.ArrayList<Date> or anything.
        // we can only have few rules:
        //  - if replacing existing element, the new one is converted (which may not be possible) to the class
        //    of the previous value
        //  - if replacing null value, we simply set the value and may the user hunt for issues
        Object oldValue = list.get(idx);
        Object newValue = pValue;
        if (oldValue != null) {
            newValue = pConverter.convert(oldValue.getClass().getName(), pValue);
        }
        list.set(idx, newValue);

        return oldValue;
    }

    /**
     * Serialize given {@link List} into a {@link JSONArray} recursively, limited by
     * {@link org.jolokia.server.core.service.serializer.SerializeOptions}
     *
     * @param pConverter
     * @param pList
     * @param pPath
     * @param pLength
     * @return
     * @throws AttributeNotFoundException
     */
    private Object listToJSON(ObjectToJsonConverter pConverter, List<?> pList, Deque<String> pPath, int pLength)
            throws AttributeNotFoundException {
        JSONArray ret = new JSONArray(pLength);
        int idx = 0;
        for (Iterator<?> it = pList.iterator(); it.hasNext() && idx < pLength; idx++ ) {
            Object item = it.next();
            // a copy passed (and drained) for each element
            Deque<String> path = new LinkedList<>(pPath);
            try {
                ret.add(pConverter.extractObject(item, path, true));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // This element is filtered out because there may bo no such attribute in an item of the list,
                // next one ...
            }
        }
        if (ret.isEmpty() && pLength > 0) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /**
     * Extract single item of a list
     *
     * @param pConverter
     * @param pList      the list to get an item from
     * @param pPath      remaining path parts passed recursively when converting element of a list
     * @param pJsonify
     * @param pPathPart  String value of list index - should be parsable to int
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractListItem(ObjectToJsonConverter pConverter, List<?> pList, Deque<String> pPath, boolean pJsonify, String pPathPart)
            throws AttributeNotFoundException {
        try {
            int idx = Integer.parseInt(pPathPart);
            return pConverter.extractObject(pList.get(idx), pPath, pJsonify);
        } catch (NumberFormatException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                new AttributeNotFoundException("Index '" + pPathPart + "' is not a numeric for accessing a list"));
        } catch (IndexOutOfBoundsException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                new AttributeNotFoundException("Index '" + pPathPart + "' is out-of-bound for a list of size " + pList.size()));
        }
    }

}
