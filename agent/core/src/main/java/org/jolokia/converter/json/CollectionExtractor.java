package org.jolokia.converter.json;

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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONArray;

/**
 * Extractor used for arbitrary collections. They are simply converted into JSON arrays, although
 * the order is arbitrary. Setting to a collection is not allowed.
 *
 * This extractor must be called after all more specialized extractors (like the {@link MapExtractor} or {@link ArrayExtractor}).
 *
 * @author roland
 * @since 18.10.11
 */
public class CollectionExtractor implements Extractor {

    /** {@inheritDoc} */
    public Class getType() {
        return Collection.class;
    }

    /**
     * Converts a collection to an JSON array. No path access is supported here
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert
     * @param pPathParts extra arguments which contain e.g. a path. The path is ignored here.
     * @param jsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathParts, boolean jsonify) throws AttributeNotFoundException {
        Collection collection = (Collection) pValue;
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        int length = pConverter.getCollectionLength(collection.size());
        if (pathPart != null) {
            return extractWithPath(pConverter, collection, pPathParts, jsonify, pathPart, length);
        } else {
            return jsonify ? extractListAsJson(pConverter, collection, pPathParts, length) : collection;
        }
    }

    private Object extractWithPath(ObjectToJsonConverter pConverter, Collection pCollection, Stack<String> pPathParts, boolean pJsonify, String pPathPart,int pLength) throws AttributeNotFoundException {
        try {
            int idx = Integer.parseInt(pPathPart);
            return pConverter.extractObject(getElement(pCollection,idx,pLength), pPathParts, pJsonify);
        } catch (NumberFormatException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is not numeric for accessing list"));
        } catch (IndexOutOfBoundsException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                    new AttributeNotFoundException("Index '" + pPathPart +  "' is out-of-bound for a list of size " + pLength));
        }
    }

    private Object getElement(Collection pCollection, int pIdx, int pLength) {
        int i = 0;
        Iterator it = pCollection.iterator();
        while (it.hasNext() && i < pLength) {
            Object val = it.next();
            if (i == pIdx) {
                return val;
            }
            i++;
        }
        throw new IndexOutOfBoundsException("Collection index " + pIdx + " larger than size " + pLength);
    }

    private Object extractListAsJson(ObjectToJsonConverter pConverter, Collection pCollection, Stack<String> pPathParts, int pLength) throws AttributeNotFoundException {
        List ret = new JSONArray();
        Iterator it = pCollection.iterator();
        for (int i = 0;i < pLength; i++) {
            Object val = it.next();
            Stack<String> path = (Stack<String>) pPathParts.clone();
            ret.add(pConverter.extractObject(val, path, true));
        }
        return ret;
    }

    /**
     * Setting of an object value is not supported for the collection converter
     */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, Object pValue) throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("A collection (beside Lists and Maps) cannot be modified");
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return false;
    }
}
