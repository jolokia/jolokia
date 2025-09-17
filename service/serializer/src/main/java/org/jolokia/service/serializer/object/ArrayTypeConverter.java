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
package org.jolokia.service.serializer.object;

import java.lang.reflect.Array;
import java.util.Collection;

import javax.management.openmbean.*;

import org.jolokia.json.JSONArray;

/**
 * Converter for {@link ArrayType} which can handle {@link java.util.Collection} values or String values
 * that can be successfully parsed into a {@link JSONArray}. This converter returns Java arrays, because there's
 * no {@code ArrayData} equivalent of {@link CompositeData} and {@link TabularData}.
 *
 * @author roland
 * @since 28.09.11
 */
class ArrayTypeConverter extends OpenTypeConverter<ArrayType<?>> {

    /**
     * Constructor
     * @param pObjectToOpenTypeConverter parent converter used for recursively converting inner values
     */
    ArrayTypeConverter(ObjectToOpenTypeConverter pObjectToOpenTypeConverter) {
        super(pObjectToOpenTypeConverter);
    }

    @Override
    public boolean canConvert(OpenType<?> pType) {
        return pType instanceof ArrayType<?>;
    }

    @Override
    public Object convert(ArrayType<?> type, Object pValue) {
        Collection<?> jsonArray = pValue instanceof Collection ? (Collection<?>) pValue
            : toJSON(pValue, JSONArray.class);

        OpenType<?> elementOpenType = type.getElementOpenType();
        Object valueArray = createTargetArray(type, jsonArray.size());
        if (type.getDimension() > 1) {
            try {
                if (type.isPrimitiveArray()) {
                    elementOpenType = ArrayType.getPrimitiveArrayType(Class.forName(type.getClassName().substring(1)));
                } else {
                    elementOpenType = new ArrayType<>(type.getDimension() - 1, elementOpenType);
                }
            } catch (OpenDataException | ClassNotFoundException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }

        int i = 0;
        for (Object element : jsonArray) {
            Array.set(valueArray, i++, objectToOpenTypeConverter.convert(elementOpenType, element));
        }

        return valueArray;
    }

    // =======================================================================

    private Object createTargetArray(OpenType<?> pElementType, int pLength) {
        if (pElementType instanceof SimpleType) {
            try {
                SimpleType<?> simpleType = (SimpleType<?>) pElementType;
                Class<?> elementClass = Class.forName(simpleType.getClassName());
                return Array.newInstance(elementClass, pLength);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Can't find class " + pElementType.getClassName() +
                                                   " for instantiating array: " + e.getMessage(),e);
            }
        } else if (pElementType instanceof CompositeType) {
            return new CompositeData[pLength];
        } else if (pElementType instanceof ArrayType) {
            // array (of arrays)* of single type.
            OpenType<?> elementType = ((ArrayType<?>) pElementType).getElementOpenType();
            try {
                Class<?> elementClass;
                if (((ArrayType<?>) pElementType).getDimension() > 1) {
                    // get rid of single leading "[". javax.management.openmbean.OpenType.getClassName() always
                    // returns a name starting with "[" if the dimensions are 2 or more
                    elementClass = Class.forName(pElementType.getClassName().substring(1));
                } else {
                    // here we'll never attempt to load an element class for "[I]".substring(1), because
                    // it'll be handled by javax.management.openmbean.ArrayType.getElementOpenType().getClassName()
                    elementClass = Class.forName(elementType.getClassName());
                    if (((ArrayType<?>) pElementType).isPrimitiveArray() && !elementClass.isPrimitive()) {
                        elementClass = toPrimitive(elementClass);
                    }
                }
                return Array.newInstance(elementClass, pLength);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Can't find class " + pElementType.getClassName() +
                                                   " for instantiating array: " + e.getMessage(),e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported array element type: " + pElementType);
        }
    }

    private Class<?> toPrimitive(Class<?> elementClass) {
        if (elementClass == Boolean.class) {
            return Boolean.TYPE;
        }
        if (elementClass == Byte.class) {
            return Byte.TYPE;
        }
        if (elementClass == Character.class) {
            return Character.TYPE;
        }
        if (elementClass == Double.class) {
            return Double.TYPE;
        }
        if (elementClass == Float.class) {
            return Float.TYPE;
        }
        if (elementClass == Integer.class) {
            return Integer.TYPE;
        }
        if (elementClass == Long.class) {
            return Long.TYPE;
        }
        if (elementClass == Short.class) {
            return Short.TYPE;
        }
        return null;
    }

}
