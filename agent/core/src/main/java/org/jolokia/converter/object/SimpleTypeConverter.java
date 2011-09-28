package org.jolokia.converter.object;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

/**
 * Converter used for simple types
 *
 * @author roland
 * @since 28.09.11
 */
class SimpleTypeConverter extends OpenTypeConverter<SimpleType> {

    private StringToObjectConverter stringToObjectConverter;

    /**
     * Constructor
     *
     * @param pDispatcher parent converter (not used here)
     * @param pStringToObjectConverter string to object converter for transforming simple types
     */
    SimpleTypeConverter(OpenTypeConverter pDispatcher, StringToObjectConverter pStringToObjectConverter) {
        super(pDispatcher);
        stringToObjectConverter = pStringToObjectConverter;
    }

    /** {@inheritDoc} */
    @Override
    boolean canConvert(OpenType pType) {
        return pType instanceof SimpleType;
    }

    /** {@inheritDoc} */
    @Override
    Object convertToObject(SimpleType pType, Object pFrom) {
        return stringToObjectConverter.prepareValue(pType.getClassName(), pFrom);
    }
}
