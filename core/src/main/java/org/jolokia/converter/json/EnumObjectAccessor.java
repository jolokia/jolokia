/*
 * Copyright 2009-2024 Roland Huss
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

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.Converter;

/**
 * {@link org.jolokia.converter.json.ObjectAccessor} for extracting enums. Enums are represented by the canonical name (Enum.name()).
 *
 * @author roland
 * @since 18.02.13
 */
public class EnumObjectAccessor implements org.jolokia.converter.json.ObjectAccessor {

    @Override
    public Class<?> getType() {
        return Enum.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        Enum<?> en = (Enum<?>) pValue;
        String name = en.name();
        if (pathPart != null) {
            if (name.equals(pathPart)) {
                return name;
            } else {
                return pConverter.getValueFaultHandler().handleException(
                        new AttributeNotFoundException("Enum value '" + name + "' doesn't match path '" + pathPart + "'" ));
            }
        }
        return pJsonify ? name : en;
    }

    @Override
    public boolean supportsStringConversion() {
        return true;
    }

    @Override
    public String extractString(Object pValue) {
        return ((Enum<?>) pValue).name();
    }

    @Override
    public boolean canSetValue() {
        return false;
    }

    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue) {
        throw new UnsupportedOperationException("An enum itself is immutable and cannot change its value");
    }

}
