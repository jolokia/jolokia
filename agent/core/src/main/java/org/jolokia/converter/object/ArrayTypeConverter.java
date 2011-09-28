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

import java.lang.reflect.Array;

import javax.management.openmbean.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;

/**
 * Converter for {@link ArrayType}
 *
 * @author roland
 * @since 28.09.11
 */
class ArrayTypeConverter extends OpenTypeConverter<ArrayType> {

    /**
     * Constructor
     * @param pDispatcher parent converter used for recursively converting
     */
    ArrayTypeConverter(OpenTypeConverter pDispatcher) {
        super(pDispatcher);
    }

    /** {@inheritDoc} */
    @Override
    public boolean canConvert(OpenType pType) {
        return pType instanceof ArrayType<?>;
    }

    /** {@inheritDoc} */
    @Override
    public Object convertToObject(ArrayType type, Object pFrom) {
        JSONAware value = toJSON(pFrom);
        // prepare each value in the array and then process the array of values
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    "Can not convert " + value + " to type " +
                    type + " because JSON object type " + value.getClass() + " is not a JSONArray");

        }

        JSONArray jsonArray = (JSONArray) value;
        OpenType elementOpenType = type.getElementOpenType();
        Object[] valueArray = createTargetArray(elementOpenType, jsonArray.size());

        int i = 0;
        for (Object element : jsonArray) {
            valueArray[i++] = getDispatcher().convertToObject(elementOpenType, element);
        }

        return valueArray;
    }

    // =======================================================================

    private Object[] createTargetArray(OpenType pElementType, int pLength) {
        if (pElementType instanceof SimpleType) {
            try {
                SimpleType simpleType = (SimpleType) pElementType;
			    Class elementClass = Class.forName(simpleType.getClassName());
                return (Object[]) Array.newInstance(elementClass, pLength);

            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Can't find class " + pElementType.getClassName() +
                                                   " for instantiating array: " + e.getMessage(),e);
            }
        } else if (pElementType instanceof CompositeType) {
            return new CompositeData[pLength];
		} else {
			throw new UnsupportedOperationException("Unsupported array element type: " + pElementType);
		}
    }
}
