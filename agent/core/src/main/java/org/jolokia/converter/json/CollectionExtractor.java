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
     * @param pExtraArgs extra arguments which contain e.g. a path. The path is ignore here
     * @param jsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs, boolean jsonify) throws AttributeNotFoundException {
        Collection collection = (Collection) pValue;
        int length = pConverter.getCollectionLength(collection.size());
        List ret;
        Iterator it = collection.iterator();
        if (!jsonify) {
            return collection;
        }

        ret = new JSONArray();
        for (int i = 0;i < length; i++) {
            Object val = it.next();
            ret.add(pConverter.extractObject(val, null, jsonify));
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
