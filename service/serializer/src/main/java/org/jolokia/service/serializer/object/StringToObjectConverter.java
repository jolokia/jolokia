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
package org.jolokia.service.serializer.object;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.ObjectName;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.ParseException;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.DateUtil;
import org.jolokia.server.core.util.EscapeUtil;

/**
 * <p>Converter from a {@link String} representation to a Java object of desired target type.
 * This converter accepts not only String values and doesn't change the value if it's already of the target type.</p>
 *
 * <p>This converted no longer calls {@link Object#toString()} as in Jolokia 1.x</p>
 *
 * @author roland
 * @since Jun 11, 2009
 */
public class StringToObjectConverter implements Deserializer<String> {

    private static final Map<String, Class<?>> TYPE_SIGNATURE_MAP = new HashMap<>();
    private static final Map<String, Class<?>> PRIMITIVE_TYPE_MAP = new HashMap<>();
    private static final Map<String, Parser> PARSER_MAP = new HashMap<>();

    private static final TemporalParser temporalParser;

    private static final BigDecimal MIN_FLOAT = new BigDecimal(-Float.MAX_VALUE);
    private static final BigDecimal MAX_FLOAT = new BigDecimal(Float.MAX_VALUE);
    private static final BigDecimal MIN_DOUBLE = new BigDecimal(-Double.MAX_VALUE);
    private static final BigDecimal MAX_DOUBLE = new BigDecimal(Double.MAX_VALUE);

    static {
        // mapping of ALL primitive types - see javax.management.openmbean.ArrayType.PRIMITIVE_ARRAY_TYPES
        // this is used to "load" classes specified as these single letters. Class.forName() can "load"
        // class specified as "[I", but not as "I"
        TYPE_SIGNATURE_MAP.put("Z", boolean.class);
        TYPE_SIGNATURE_MAP.put("C", char.class);
        TYPE_SIGNATURE_MAP.put("B", byte.class);
        TYPE_SIGNATURE_MAP.put("S", short.class);
        TYPE_SIGNATURE_MAP.put("I", int.class);
        TYPE_SIGNATURE_MAP.put("J", long.class);
        TYPE_SIGNATURE_MAP.put("F", float.class);
        TYPE_SIGNATURE_MAP.put("D", double.class);

        PRIMITIVE_TYPE_MAP.put(boolean.class.getName(), Boolean.class);
        PRIMITIVE_TYPE_MAP.put(char.class.getName(), Character.class);
        PRIMITIVE_TYPE_MAP.put(byte.class.getName(), Byte.class);
        PRIMITIVE_TYPE_MAP.put(short.class.getName(), Short.class);
        PRIMITIVE_TYPE_MAP.put(int.class.getName(), Integer.class);
        PRIMITIVE_TYPE_MAP.put(long.class.getName(), Long.class);
        PRIMITIVE_TYPE_MAP.put(float.class.getName(), Float.class);
        PRIMITIVE_TYPE_MAP.put(double.class.getName(), Double.class);

        // parsers of all "basic" types - not 1:1 with types defined in JMX 1.4 specification
        // "3.2 - Basic Data Types", Table 1 for Open MBeans basic data types

        // all 8 primitive data types from Java
        BooleanParser booleanParser = new BooleanParser();
        PARSER_MAP.put(Boolean.class.getName(), booleanParser);
        PARSER_MAP.put("boolean", booleanParser);
        CharacterParser characterParser = new CharacterParser();
        PARSER_MAP.put(Character.class.getName(), characterParser);
        PARSER_MAP.put("char", characterParser);
        ByteParser byteParser = new ByteParser();
        PARSER_MAP.put(Byte.class.getName(), byteParser);
        PARSER_MAP.put("byte", byteParser);
        ShortParser shortParser = new ShortParser();
        PARSER_MAP.put(Short.class.getName(), shortParser);
        PARSER_MAP.put("short", shortParser);
        IntegerParser integerParser = new IntegerParser();
        PARSER_MAP.put(Integer.class.getName(), integerParser);
        PARSER_MAP.put("int", integerParser);
        LongParser longParser = new LongParser();
        PARSER_MAP.put(Long.class.getName(), longParser);
        PARSER_MAP.put("long", longParser);
        FloatParser floatParser = new FloatParser();
        PARSER_MAP.put(Float.class.getName(), floatParser);
        PARSER_MAP.put("float", floatParser);
        DoubleParser doubleParser = new DoubleParser();
        PARSER_MAP.put(Double.class.getName(), doubleParser);
        PARSER_MAP.put("double", doubleParser);

        // java.lang, java.util, javax.management types for Open MBeans
        PARSER_MAP.put(String.class.getName(), new StringParser());
        PARSER_MAP.put(BigDecimal.class.getName(), new BigDecimalParser());
        PARSER_MAP.put(BigInteger.class.getName(), new BigIntegerParser());
        PARSER_MAP.put(Date.class.getName(), new DateParser());
        PARSER_MAP.put(ObjectName.class.getName(), new ObjectNameParser());

        // javax.management.openmbean.ArrayType/CompositeType/TabularType for Open MBeans (?)

        // other simple types not part of Open MBeans
        PARSER_MAP.put(URL.class.getName(), new URLParser());
        PARSER_MAP.put(Calendar.class.getName(), new CalendarParser());

        // types that can be parser from JSON
        JSONParser jsonParser = new JSONParser();
        PARSER_MAP.put(List.class.getName(), jsonParser);
        PARSER_MAP.put(JSONArray.class.getName(), jsonParser);
        PARSER_MAP.put(Map.class.getName(), jsonParser);
        PARSER_MAP.put(JSONObject.class.getName(), jsonParser);

        // for target types where instanceof / java.lang.Class.isAssignableFrom() can be used - we can't
        // simply put the type into PARSER_MAP
        temporalParser = new TemporalParser();
    }

    /**
     * Convert value from either a given object or its string representation.
     * If the value is already assignable to the given class name it is returned directly.
     *
     * @param pExpectedClassName type name of the expected type
     * @param pValue value to either take directly or to convert from its string representation.
     * @return the converted object which is of type {@code pExpectedClassName}
     */
    @Override
    public Object deserialize(String pExpectedClassName, Object pValue) {
        if (pValue == null) {
            return null;
        }

        Class<?> expectedClass;
        if (TYPE_SIGNATURE_MAP.containsKey(pExpectedClassName)) {
            expectedClass = TYPE_SIGNATURE_MAP.get(pExpectedClassName);
        } else if (PRIMITIVE_TYPE_MAP.containsKey(pExpectedClassName)) {
            expectedClass = PRIMITIVE_TYPE_MAP.get(pExpectedClassName);
        } else {
            expectedClass = ClassUtil.classForName(pExpectedClassName);
        }

        if (expectedClass == null) {
            throw new IllegalArgumentException("Cannot load a class of target type \"" + pExpectedClassName + "\"");
        }

        // we know the class
        return deserialize(expectedClass, pValue);
    }

    /**
     * Convert value from either a given object or its string representation. But we already know the target class.
     *
     * @param pExpectedClass the expected type
     * @param pValue value to either take directly or to convert from its string representation.
     * @return the converted object which is of type {@code pExpectedClassName}
     */
    private Object deserialize(Class<?> pExpectedClass, Object pValue) {
        // quick win without conversion
        if (pExpectedClass.isAssignableFrom(pValue.getClass())) {
            if (pExpectedClass == String.class) {
                return convertFromString(String.class, (String) pValue);
            }
            return pValue;
        }

        // enums, if converting from String
        if (Enum.class.isAssignableFrom(pExpectedClass)) {
            if (!(pValue instanceof String)) {
                throw new IllegalArgumentException("Cannot convert non-String values to enums");
            }
            //noinspection rawtypes,unchecked
            return Enum.valueOf((Class) pExpectedClass, (String) pValue);
        }

        // arrays, if converting from collections
        if (pExpectedClass.isArray() && pValue instanceof Collection) {
            return convertCollectionToArray(pExpectedClass, (Collection<?>) pValue);
        }

        if (pExpectedClass.isPrimitive()) {
            Class<?> wrapperClass = PRIMITIVE_TYPE_MAP.get(pExpectedClass.getName());
            if (wrapperClass.isAssignableFrom(pValue.getClass())) {
                return pValue;
            }
        }

        if (pValue.getClass() != String.class) {
            // try parser by direct class mapping for non-String argument
            Parser parser = PARSER_MAP.get(pExpectedClass.getName());
            if (parser != null && parser.supports(pValue.getClass(), pValue)) {
                return parser.parse(pValue);
            }

            if (pExpectedClass.getPackage().getName().equals("java.time") && temporalParser.supports(pValue.getClass(), pValue)) {
                // we'll try to parse non-String as some Temporal values
                return temporalParser.extract(pExpectedClass, pValue);
            }

            // the only thing we can try is 1-arg constructor matching the type of the converted value
            Object result = convertByConstructor(pExpectedClass, pValue);
            if (result != null) {
                return result;
            }
            throw new IllegalArgumentException("Cannot convert a \"" + pValue.getClass().getName()
                + "\" value to \"" + pExpectedClass.getName() + "\"");
        }

        // real String - Object conversion
        return convertFromString(pExpectedClass, (String) pValue);
    }

    /**
     * Converts a {@link Collection} to an array of the given type ({@link Class#isArray()} is true for the
     * target type)
     *
     * @param pType
     * @param pList
     * @return
     */
    private Object convertCollectionToArray(Class<?> pType, Collection<?> pList) {
        Class<?> valueType = pType.getComponentType();
        Object ret = Array.newInstance(valueType, pList.size());
        int i = 0;
        for (Object value : pList) {
            if (value == null) {
                if (!valueType.isPrimitive()) {
                    Array.set(ret, i++, null);
                } else {
                    throw new IllegalArgumentException("Cannot use a null value in an array of type " + valueType.getSimpleName());
                }
            } else {
                if (valueType.isAssignableFrom(value.getClass())) {
                    // Can be set directly
                    Array.set(ret, i++, value);
                } else if (valueType.isArray() && value instanceof Collection) {
                    // array of arrays
                    Array.set(ret, i++, convertCollectionToArray(valueType, (Collection<?>) value));
                } else {
                    // Try to convert in generic way
                    Array.set(ret, i++, deserialize(valueType, value));
                }
            }
        }
        return ret;
    }

    /**
     * Deserialize a string representation to an object for a given type specified as String
     *
     * @param pType type to convert to
     * @param pValue the value to convert from
     * @return the converted value
     */
    Object convertFromString(String pType, String pValue) {
        if (pType.startsWith("[")) {
            if (pType.length() >= 2) {
                Class<Object> arrayClass = ClassUtil.classForName(pType);
                if (arrayClass != null) {
                    return convertFromString(arrayClass, pValue);
                }
            }
            throw new IllegalArgumentException("Cannot load a class of target type \"" + pType + "\"");
        }

        Class<?> type;
        if (TYPE_SIGNATURE_MAP.containsKey(pType)) {
            type = TYPE_SIGNATURE_MAP.get(pType);
        } else {
            type = ClassUtil.classForName(pType);
        }
        if (type == null) {
            type = PRIMITIVE_TYPE_MAP.get(pType);
        }
        if (type != null) {
            return convertFromString(type, pValue);
        }

        throw new IllegalArgumentException("Cannot load a class of target type \"" + pType + "\"");
    }

    /**
     * Deserialize a string representation to an object for a given type specified as resolved Class
     *
     * @param pType type to convert to
     * @param pValue the value to convert from
     * @return the converted value
     */
    Object convertFromString(Class<?> pType, String pValue) {
        String value = EscapeUtil.convertSpecialStringTags(pValue);
        if (value == null) {
            return null;
        }

        // convert to array - items will use the same conversion plan
        if (pType.isArray()) {
            return convertToArray(pType, pValue);
        }

        // try parser by direct class mapping
        Parser parser = PARSER_MAP.get(pType.getName());
        if (parser != null) {
            return parser.parse(value);
        }

        if (pType.getPackage().getName().equals("java.time")) {
            // we'll try to parse as some Temporal values
            return temporalParser.extract(pType, value);
        }

        // old school String to Object conversion
        Object cValue = convertByConstructor(pType, pValue);
        if (cValue != null) {
        	return cValue;
        }

        throw new IllegalArgumentException(
                "Cannot convert string " + value + " to type " +
                        pType + " because no converter could be found");
    }

    /**
     * Converts a String value to a single-dimensional array specified as Class ({@link Class#isArray()} is true).
     * @param pType
     * @param pValue
     * @return
     */
    private Object convertToArray(Class<?> pType, String pValue) {
        if (!pType.isArray()) {
            throw new IllegalArgumentException("Expected array Class, got " + pType.getName());
        }
        if (pType.getComponentType().isArray()) {
            throw new IllegalArgumentException("Can parse only single-dimensional arrays");
        }

        Class<?> valueType = pType.getComponentType();
        String[] values = EscapeUtil.splitAsArray(pValue, EscapeUtil.PATH_ESCAPE, ",");

        Object ret = Array.newInstance(valueType, values.length);
        int i = 0;
        for (String value : values) {
            Array.set(ret, i++, value.equals("[null]") ? null : deserialize(valueType, value));
        }
        return ret;
    }

    /**
     * Try to convert String to an Object by 1-arg String constructor
     * @param pType
     * @param pValue
     * @return
     */
    private Object convertByConstructor(Class<?> pType, Object pValue) {
        for (Constructor<?> constructor : pType.getConstructors()) {
            // support only 1 constructor parameter
            if (constructor.getParameterTypes().length == 1 &&
                constructor.getParameterTypes()[0].isAssignableFrom(pValue.getClass())) {
                try {
                    return constructor.newInstance(pValue);
                } catch (Exception ignore) { }
            }
        }

        return null;
    }

    // ===========================================================================

    /**
     * Checks if the value is an integer {@link Number} that fits into a value with given number of non-sign bits.
     * @param pValue
     * @param nClass
     * @param bits
     * @param minV
     * @param maxV
     * @return
     */
    private static boolean noIntegerOverflow(Object pValue, Class<? extends Number> nClass, int bits, long minV, long maxV) {
        if (!(pValue instanceof Number)) {
            return false;
        }
        if (nClass == pValue.getClass()) {
            return true;
        }
        if (pValue instanceof BigInteger) {
            BigInteger bi = (BigInteger) pValue;
            return bi.bitLength() <= bits;
        }
        // range check
        long v = ((Number) pValue).longValue();
        return v >= minV && v <= maxV;
    }

    /**
     * Basic parser interface for converting Strings to values
     */
    private interface Parser {
        /**
         * Parse a particular String value into some value of specific type
         * @param pValue value to extract
         * @return the extracted value
         */
        Object parse(String pValue);

        /**
         * Parse supported non-String value
         * @param pValue
         * @return
         */
        default Object parse(Object pValue) {
            return null;
        }

        /**
         * A parser may support non-String arguments as well.
         * @param pValueClass
         * @param pValue
         * @return
         */
        default boolean supports(Class<?> pValueClass, Object pValue) {
            return false;
        }
    }

    // implementations for direct type matching

    private static class BooleanParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Boolean.parseBoolean(pValue);
        }
    }

    private static class CharacterParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return pValue.charAt(0);
        }
    }

    private static class ByteParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Byte.parseByte(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return ((Number) pValue).byteValue();
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return noIntegerOverflow(pValue, Byte.class, 7, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
    }

    private static class ShortParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Short.parseShort(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return ((Number) pValue).shortValue();
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return noIntegerOverflow(pValue, Short.class, 15, Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }

    private static class IntegerParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Integer.parseInt(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return ((Number) pValue).intValue();
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return noIntegerOverflow(pValue, Integer.class, 31, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
    }

    private static class LongParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Long.parseLong(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return ((Number) pValue).longValue();
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return noIntegerOverflow(pValue, Long.class, 63, Long.MIN_VALUE, Long.MAX_VALUE);
        }

    }

    private static class FloatParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Float.parseFloat(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return ((Number) pValue).floatValue();
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            if (!(pValue instanceof Number)) {
                return false;
            }
            if (pValue.getClass() == Float.class) {
                return true;
            }
            if (pValue instanceof BigDecimal) {
                return ((BigDecimal) pValue).compareTo(MAX_FLOAT) >= 0 && ((BigDecimal) pValue).compareTo(MIN_FLOAT) >= 0;
            } else if (pValue instanceof Double) {
                double v = (double) pValue;
                return v >= Float.MIN_VALUE && v <= Float.MAX_VALUE;
            }
            return false;
        }
    }

    private static class DoubleParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return Double.parseDouble(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return ((Number) pValue).doubleValue();
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            if (!(pValue instanceof Number)) {
                return false;
            }
            if (pValue.getClass() == Double.class) {
                return true;
            }
            if (pValue instanceof BigDecimal) {
                return ((BigDecimal) pValue).compareTo(MAX_DOUBLE) <= 0 && ((BigDecimal) pValue).compareTo(MIN_DOUBLE) >= 0;
            } else if (pValue instanceof Float) {
                return true;
            }
            return false;
        }
    }

    private static class StringParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return pValue;
        }
    }

    private static class BigDecimalParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return new BigDecimal(pValue);
        }
    }

    private static class BigIntegerParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return new BigInteger(pValue);
        }
    }

    private static class DateParser implements Parser {
        @Override
        public Object parse(String pValue) {
            try {
                long time = Long.parseLong(pValue);
                return new Date(time);
            } catch (NumberFormatException exp) {
                return DateUtil.fromISO8601(pValue);
            }
        }
    }

    private static class ObjectNameParser implements Parser {
        @Override
        public Object parse(String pValue) {
            try {
                return new javax.management.ObjectName(pValue);
            } catch (javax.management.MalformedObjectNameException e) {
                throw new IllegalArgumentException("Cannot parse ObjectName " + pValue + ": " + e.getMessage(), e);
            }
        }
    }

    private static class URLParser implements Parser {
        @Override
        public Object parse(String pValue) {
            try {
                return new URL(pValue);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Cannot parse URL " + pValue + ": " + e, e);
            }
        }
    }

    private static class CalendarParser implements Parser {
        @Override
        public Object parse(String pValue) {
            Calendar result = Calendar.getInstance();
            try {
                long time = Long.parseLong(pValue);
                result.setTime(new Date(time));
                return result;
            } catch (NumberFormatException exp) {
                Date date = DateUtil.fromISO8601(pValue);
                result.setTime(date);
                return result;
            }
        }
    }

    // implementations for base type matching

    private static class JSONParser implements Parser {
        @Override
        public Object parse(String pValue) {
            try {
                // parser can give us JSONStructure, but also just Strings, Numbers or null
                return new org.jolokia.json.parser.JSONParser().parse(pValue);
            } catch (ParseException | IOException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e, e);
            }
        }
    }

    private static class TemporalParser {
        public Object extract(Class<?> temporalType, String pValue) {
            // client should send a unix nano time
            try {
                long unixNano = Long.parseLong(pValue);
                return extract(temporalType, unixNano);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Cannot parse Temporal " + pValue + ": " + e, e);
            }
        }

        public Object extract(Class<?> temporalType, Object pValue) {
            long unixNano = ((Number) pValue).longValue();
            // we assume that an instant is always in UTC
            Instant instant = Instant.ofEpochSecond(unixNano / 1_000_000_000, unixNano % 1_000_000_000);
            if (temporalType == Instant.class) {
                return instant;
            }
            if (temporalType == OffsetDateTime.class) {
                return OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"));
            }
            if (temporalType == ZonedDateTime.class) {
                return ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));
            }
            throw new IllegalArgumentException("Cannot handle Temporal of class \"" + temporalType + "\" for value " + pValue);
        }

        public boolean supports(Class<?> pValueClass, Object pValue) {
            return noIntegerOverflow(pValue, Long.class, 63, Long.MIN_VALUE, Long.MAX_VALUE);
        }
    }

}
