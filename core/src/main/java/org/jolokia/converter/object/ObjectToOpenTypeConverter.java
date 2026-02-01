/*
 * Copyright 2009-2011 Roland Huss
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.jolokia.converter.object;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

/**
 * <p>Dedicated {@link org.jolokia.converter.object.Converter} which specifies target type as {@link OpenType}. It handles null values
 * and delegates non-null values to one of four implementations of {@link OpenTypeConverter} for this fixed
 * list of {@liok OpenType open types}:<ul>
 *     <li>{@link javax.management.openmbean.SimpleType}</li>
 *     <li>{@link javax.management.openmbean.ArrayType}</li>
 *     <li>{@link javax.management.openmbean.CompositeType}</li>
 *     <li>{@link javax.management.openmbean.TabularType}</li>
 * </ul>
 * </p>
 *
 * <p>Delegation to {@link OpenTypeConverter} introduces some constraints on the type of values that can be converted.
 * <ul>
 *     <li>{@link SimpleTypeConverter} handles simple types and it delegates (<em>back</em>) to generic
 *     {@link ObjectToObjectConverter}.</li>
 *     <li>{@link ArrayTypeConverter} handles multidimensional arrays of objects, so the value being converted
 *     has to be a {@link java.util.Collection} or a String parsed using {@link org.jolokia.json.parser.JSONParser}
 *     into a {@link org.jolokia.json.JSONArray}.</li>
 *     <li>{@link CompositeTypeConverter} handles {@link java.util.Map} values (possibly parsed from strings into
 *     {@link org.jolokia.json.JSONObject}) and converts them to {@link javax.management.openmbean.CompositeData}
 *     according to information specified in {@link javax.management.openmbean.CompositeType}.</li>
 *     <li>{@link TabularDataConverter} handles {@link javax.management.openmbean.TabularType}</li>
 * </ul>
 * </p>
 *
 * @author Assaf Berg, roland
 * @since 02.08.11
 */
public class ObjectToOpenTypeConverter implements Converter<OpenType<?>> {

    private static final Map<String, SimpleType<?>> PRIMITIVE_OPENTYPE_MAP = new HashMap<>();
    private static final Map<Class<?>, SimpleType<?>> SIMPLE_OPENTYPE_MAP = new HashMap<>();

    protected final boolean forgiving;

    // List of converters used
    private final List<OpenTypeConverter<? extends OpenType<?>>> converters;

    static {
        // All known simple OpenTypes
        PRIMITIVE_OPENTYPE_MAP.put(boolean.class.getName(), SimpleType.BOOLEAN);
        PRIMITIVE_OPENTYPE_MAP.put(char.class.getName(), SimpleType.CHARACTER);
        PRIMITIVE_OPENTYPE_MAP.put(byte.class.getName(), SimpleType.BYTE);
        PRIMITIVE_OPENTYPE_MAP.put(short.class.getName(), SimpleType.SHORT);
        PRIMITIVE_OPENTYPE_MAP.put(int.class.getName(), SimpleType.INTEGER);
        PRIMITIVE_OPENTYPE_MAP.put(long.class.getName(), SimpleType.LONG);
        PRIMITIVE_OPENTYPE_MAP.put(float.class.getName(), SimpleType.FLOAT);
        PRIMITIVE_OPENTYPE_MAP.put(double.class.getName(), SimpleType.DOUBLE);

        SIMPLE_OPENTYPE_MAP.put(boolean.class, SimpleType.BOOLEAN);
        SIMPLE_OPENTYPE_MAP.put(char.class, SimpleType.CHARACTER);
        SIMPLE_OPENTYPE_MAP.put(byte.class, SimpleType.BYTE);
        SIMPLE_OPENTYPE_MAP.put(short.class, SimpleType.SHORT);
        SIMPLE_OPENTYPE_MAP.put(int.class, SimpleType.INTEGER);
        SIMPLE_OPENTYPE_MAP.put(long.class, SimpleType.LONG);
        SIMPLE_OPENTYPE_MAP.put(float.class, SimpleType.FLOAT);
        SIMPLE_OPENTYPE_MAP.put(double.class, SimpleType.DOUBLE);
        SIMPLE_OPENTYPE_MAP.put(Boolean.class, SimpleType.BOOLEAN);
        SIMPLE_OPENTYPE_MAP.put(Character.class, SimpleType.CHARACTER);
        SIMPLE_OPENTYPE_MAP.put(Byte.class, SimpleType.BYTE);
        SIMPLE_OPENTYPE_MAP.put(Short.class, SimpleType.SHORT);
        SIMPLE_OPENTYPE_MAP.put(Integer.class, SimpleType.INTEGER);
        SIMPLE_OPENTYPE_MAP.put(Long.class, SimpleType.LONG);
        SIMPLE_OPENTYPE_MAP.put(Float.class, SimpleType.FLOAT);
        SIMPLE_OPENTYPE_MAP.put(Double.class, SimpleType.DOUBLE);
        SIMPLE_OPENTYPE_MAP.put(String.class, SimpleType.STRING);
        SIMPLE_OPENTYPE_MAP.put(ObjectName.class, SimpleType.OBJECTNAME);
        SIMPLE_OPENTYPE_MAP.put(Void.class, SimpleType.VOID);
        SIMPLE_OPENTYPE_MAP.put(Date.class, SimpleType.DATE);
        SIMPLE_OPENTYPE_MAP.put(BigInteger.class, SimpleType.BIGINTEGER);
        SIMPLE_OPENTYPE_MAP.put(BigDecimal.class, SimpleType.BIGDECIMAL);
    }

    /**
     * Constructor
     *
     * @param pObjectToObjectConverter converter for the <em>leaf</em> values which do not contain inner values.
     */
    public ObjectToOpenTypeConverter(Converter<String> pObjectToObjectConverter, boolean pForgiving) {
        converters = Arrays.asList(
            new SimpleTypeConverter(this, pObjectToObjectConverter),
            new ArrayTypeConverter(this),
            new CompositeTypeConverter(this),
            new TabularDataConverter(this)
        );
        this.forgiving = pForgiving;
    }

    public static SimpleType<?> knownPrimitiveOpenType(String type) {
        return PRIMITIVE_OPENTYPE_MAP.get(type);
    }

    public static SimpleType<?> knownSimpleOpenType(Class<?> cls) {
        return SIMPLE_OPENTYPE_MAP.get(cls);
    }

    /**
     * Handle conversion for OpenTypes. The value is expected to be in JSON (either
     * a {@link org.jolokia.json.JSONStructure} object or its string representation.
     *
     * @param pOpenType target type
     * @param pValue value to convert from
     * @return the converted value
     */
    @Override
    public Object convert(OpenType<?> pOpenType, Object pValue) {
        if (pValue == null) {
            return null;
        } else {
            for (OpenTypeConverter<? extends OpenType<?>> converter : converters) {
                if (converter.canConvert(pOpenType)) {
                    return invokeConverter(converter, pOpenType, pValue);
                }
            }
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to " + pOpenType + ": " + "No converter could be found");
        }
	}

    // capture helper trick
    private <T extends OpenType<?>> Object invokeConverter(OpenTypeConverter<T> converter, OpenType<?> pOpenType, Object pValue) {
        @SuppressWarnings("unchecked")
        T casted = (T) pOpenType;
        return converter.convert(casted, pValue);
    }

    public boolean isForgiving() {
        return forgiving;
    }

}
