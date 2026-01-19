/*
 * Copyright 2009-2025 Roland Huss
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

import org.jolokia.converter.object.Converter;

public class SimpleAccessor implements org.jolokia.converter.json.ObjectAccessor {

    @Override
    public Class<?> getType() {
        // special handling by ObjectToJsonConverter
        return null;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, boolean pJsonify) {
        Class<?> clazz = pValue.getClass();

        if (!pJsonify) {
            // don't do anything
            return pValue;
        }

        // generally we should call pConverter.getConverter().convert(String.class.getName(), pValue)
        // but: 1) some values are already valid JSON objects (like Long or String), 2) we can save thread stack
        //
        // our JSON parser handles can return only few types, so we can't blindly return .toString() or return a value
        // without conversion. For example we should never return a char, Character or int!
        if (ObjectToJsonConverter.JSON_TYPES.contains(clazz)) {
            // one special handling for Longs
            if (clazz == Long.class && "string".equals(pConverter.getSerializeLong())) {
                // Long value can exceed max safe integer in JS, so convert it to
                // a string when the option is specified
                return Long.toString((Long) pValue);
            }
            // proper JSON value. For example int/Integer should be converted to long
            return pValue;
        }

        // all other values should be converted using proper converter and target class
        if (ObjectToJsonConverter.JSON_CONVERSIONS.containsKey(clazz)) {
            return pConverter.getConverter().convert(ObjectToJsonConverter.JSON_CONVERSIONS.get(clazz), pValue);
        }

        // fallback to String conversion
        return pConverter.getConverter().convert(String.class.getName(), pValue);
    }

    @Override
    public boolean canSetValue() {
        return false;
    }

    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue) {
        throw new UnsupportedOperationException("Basic type is immutable and cannot change its value");
    }

}
