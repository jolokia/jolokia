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
package org.jolokia.converter.object;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.converter.json.DateFormatConfiguration;
import org.jolokia.converter.json.ObjectAccessor;
import org.jolokia.core.config.CoreConfiguration;
import org.jolokia.core.util.ClassUtil;
import org.jolokia.core.util.DateUtil;
import org.jolokia.core.util.EscapeUtil;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.ParseException;

/**
 * <p>Converter from some object representation to desired target type.
 * This converter accepts not only String values and doesn't change the value if it's already of the target type.</p>
 *
 * <p>This converted no longer calls {@link Object#toString()} as in Jolokia 1.x.</p>
 *
 * <p>Internally this class uses parser abstractions for several built-in types.</p>
 *
 * @author roland
 * @since Jun 11, 2009
 */
public class ObjectToObjectConverter implements Converter<String> {

    private static final Map<String, Class<?>> TYPE_SIGNATURE_MAP = new HashMap<>();
    private static final Map<String, Class<?>> PRIMITIVE_TYPE_MAP = new HashMap<>();
    private static final Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<>();
    private static final Map<String, Parser> PARSER_MAP = new HashMap<>();

    // String parser is special, because it can be configured with discovered simplifiers
    private static final StringParser stringParser;
    // date/calendar/temporal parsers can be configured with date format
    private static final DateParser dateParser;
    private static final CalendarParser calendarParser;
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

        PRIMITIVE_TYPES.put(boolean.class.getName(), Boolean.TYPE);
        PRIMITIVE_TYPES.put(char.class.getName(), Character.TYPE);
        PRIMITIVE_TYPES.put(byte.class.getName(), Byte.TYPE);
        PRIMITIVE_TYPES.put(short.class.getName(), Short.TYPE);
        PRIMITIVE_TYPES.put(int.class.getName(), Integer.TYPE);
        PRIMITIVE_TYPES.put(long.class.getName(), Long.TYPE);
        PRIMITIVE_TYPES.put(float.class.getName(), Float.TYPE);
        PRIMITIVE_TYPES.put(double.class.getName(), Double.TYPE);

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
        stringParser = new StringParser();
        PARSER_MAP.put(String.class.getName(), stringParser);
        PARSER_MAP.put(BigDecimal.class.getName(), new BigDecimalParser());
        PARSER_MAP.put(BigInteger.class.getName(), new BigIntegerParser());
        dateParser = new DateParser();
        PARSER_MAP.put(Date.class.getName(), dateParser);
        PARSER_MAP.put(ObjectName.class.getName(), new ObjectNameParser());

        // javax.management.openmbean.ArrayType/CompositeType/TabularType for Open MBeans (?)

        // other simple types not part of Open MBeans
        PARSER_MAP.put(URL.class.getName(), new URLParser());
        PARSER_MAP.put(URI.class.getName(), new URIParser());
        // Calendar is not an actual class of object being parsed, but a target interface and we don't
        // use instanceof here
        calendarParser = new CalendarParser();
        PARSER_MAP.put(Calendar.class.getName(), calendarParser);

        // types that can be parsed from JSON
        // while List and Map are interfaces, we don't use pValue.getClass() as the key, only the desired
        // target type
        JSONParser jsonParser = new JSONParser();
        PARSER_MAP.put(List.class.getName(), jsonParser);
        PARSER_MAP.put(JSONArray.class.getName(), jsonParser);
        PARSER_MAP.put(Map.class.getName(), jsonParser);
        PARSER_MAP.put(JSONObject.class.getName(), jsonParser);

        // for target types where instanceof/java.lang.Class.isAssignableFrom()/package can be used - we can't
        // simply put the type into PARSER_MAP
        temporalParser = new TemporalParser();
    }

    public ObjectToObjectConverter() {
        // will create default date format configuration
        setCoreConfiguration(null);
    }

    /**
     * Helper methods to reuse the static type mapping contained in this converter
     * @param typeName
     * @return
     */
    public static Class<?> knownTypeByName(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return null;
        }
        if (PRIMITIVE_TYPES.containsKey(typeName)) {
            return PRIMITIVE_TYPES.get(typeName);
        }
        if (typeName.length() == 1 && TYPE_SIGNATURE_MAP.containsKey(typeName)) {
            return PRIMITIVE_TYPES.get(TYPE_SIGNATURE_MAP.get(typeName).getName());
        }
        if (!typeName.contains(".") && typeName.startsWith("[")) {
            return ClassUtil.classForName(typeName);
        }

        return null;
    }

    /**
     * When this converter is configured with {@link CoreConfiguration}, we can get configuration of some
     * conversion-related properties like date format.
     *
     * @param coreConfiguration
     */
    public void setCoreConfiguration(CoreConfiguration coreConfiguration) {
        // better configuration of date formats
        DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration(coreConfiguration);

        stringParser.setDateFormatConfiguration(dateFormatConfiguration);
        dateParser.setDateFormatConfiguration(dateFormatConfiguration);
        calendarParser.setDateFormatConfiguration(dateFormatConfiguration);
        temporalParser.setDateFormatConfiguration(dateFormatConfiguration);
    }

    /**
     * We can configure {@link ObjectAccessor#supportsStringConversion() stringConverters} for discoverable
     * String serialization for custom (and some built-in) types
     *
     * @param stringConverters
     */
    public void setStringConverters(Map<Class<?>, ObjectAccessor> stringConverters) {
        stringParser.setStringConverters(stringConverters);
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
    public Object convert(String pExpectedClassName, Object pValue) {
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
        return convert(expectedClass, pValue);
    }

    /**
     * Convert value from either a given object or its string representation. But we already know the target class.
     *
     * @param pExpectedClass the expected type
     * @param pValue value to either take directly or to convert from its string representation.
     * @return the converted object which is of type {@code pExpectedClassName}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Class<?> pExpectedClass, Object pValue) {
        // quick win without conversion
        if (pExpectedClass.isAssignableFrom(pValue.getClass())) {
            if (pExpectedClass == String.class) {
                return EscapeUtil.convertSpecialStringTags((String) pValue);
            }
            return pValue;
        }

        // enums, if converting from String
        if (Enum.class.isAssignableFrom(pExpectedClass)) {
            if (!(pValue instanceof String)) {
                throw new IllegalArgumentException("Cannot convert non-String values to enums");
            }
            return Enum.valueOf((Class) pExpectedClass, (String) pValue);
        }

        // arrays, if converting from collections
        if (pExpectedClass.isArray() && pValue instanceof Collection) {
            return convertCollectionToArray(pExpectedClass, (Collection<?>) pValue);
        }

        // sets, if converting from collections - latest addition, not needed until I started
        // refactoring jolokia-client-jmx-adapter
        if (pExpectedClass.isAssignableFrom(Set.class) && pValue instanceof Collection collection) {
            return new HashSet<>(collection);
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

            if (pExpectedClass.getPackage() == Instant.class.getPackage() && temporalParser.supports(pValue.getClass(), pValue)) {
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
                    Array.set(ret, i++, convert(valueType, value));
                }
            }
        }
        return ret;
    }

    /**
     * Convert a string representation to an object for a given type specified as String
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
     * Convert a string representation to an object of a given type specified as resolved Class
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
            Array.set(ret, i++, value.equals("[null]") ? null : convert(valueType, value));
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
        if (pValue instanceof BigInteger bi) {
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

    /**
     * Interface for {@link Parser parsers} which want to use specific {@link DateFormatConfiguration}
     */
    private interface DateFormatConfigurationAware {
        /**
         * Configure with {@link DateFormatConfiguration}
         * @param configuration
         */
        void setDateFormatConfiguration(DateFormatConfiguration configuration);
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
            if (pValue instanceof BigDecimal bi) {
                return bi.compareTo(MAX_FLOAT) <= 0 && bi.compareTo(MIN_FLOAT) >= 0;
            } else if (pValue instanceof BigInteger bi) {
                BigDecimal bd = new BigDecimal(bi.toString());
                return bd.compareTo(MAX_FLOAT) <= 0 && bd.compareTo(MIN_FLOAT) >= 0;
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
            if (pValue instanceof BigDecimal bd) {
                return bd.compareTo(MAX_DOUBLE) <= 0 && bd.compareTo(MIN_DOUBLE) >= 0;
            } else if (pValue instanceof BigInteger bi) {
                BigDecimal bd = new BigDecimal(bi.toString());
                return bd.compareTo(MAX_DOUBLE) <= 0 && bd.compareTo(MIN_DOUBLE) >= 0;
            } else if (pValue instanceof Float) {
                return true;
            }
            return false;
        }
    }

    /**
     * <p>String {@link Parser} is a bit special. We want to avoid blindly calling {@code .toString()} when converting
     * to strings. To String conversion is however very important if we need strings - for example
     * as {@link Map} keys when serializing maps to JSON. This means we need to be able to explicitly
     * support several types as potential map keys.</p>
     *
     * <p>Some JDK classes are known for having good {@code .toString()} implementation and we should use them.
     * However {@link Date#toString()} is not something we should rely on.</p>
     *
     * <p>For example see <a href="https://github.com/jolokia/jolokia/issues/732">jolokia/jolokia#732</a> when
     * handling {@code Map<InetAddress, Float>} for Cassandra.</p>
     */
    private static class StringParser implements Parser, DateFormatConfigurationAware {

        // cache for classes with .toString() that we can call or will never call
        private static final Set<Class<?>> KNOWN_TO_STRING = Collections.synchronizedSet(new HashSet<>());
        private static final Set<Class<?>> UNDESIRED_TO_STRING = new HashSet<>();

        private static final Map<Class<?>, ObjectAccessor> ACCESSORS = new HashMap<>();
        private static final Map<Class<?>, ObjectAccessor> ALL_ACCESSORS = Collections.synchronizedMap(new HashMap<>());

        static {
            // All javax.management.openmbean.SimpleTypes for OpenMBeans
            KNOWN_TO_STRING.add(Boolean.class);
            KNOWN_TO_STRING.add(Character.class);
            KNOWN_TO_STRING.add(Byte.class);
            KNOWN_TO_STRING.add(Short.class);
            KNOWN_TO_STRING.add(Integer.class);
            KNOWN_TO_STRING.add(Long.class);
            KNOWN_TO_STRING.add(Float.class);
            KNOWN_TO_STRING.add(Double.class);
            KNOWN_TO_STRING.add(BigInteger.class);
            KNOWN_TO_STRING.add(BigDecimal.class);
            UNDESIRED_TO_STRING.add(Date.class);
            // supported as DateAccessor
//            SUPPORTED.add(Date.class);
            // supported as simplifier
//            KNOWN_TO_STRING.add(ObjectName.class);

            // java.time - uses configurable patterns
            // supported via JavaTimeTemporalAccessor
//            SUPPORTED.add(Instant.class);
//            SUPPORTED.add(LocalDate.class);
//            SUPPORTED.add(LocalDateTime.class);
//            SUPPORTED.add(LocalTime.class);
//            SUPPORTED.add(OffsetDateTime.class);
//            SUPPORTED.add(OffsetTime.class);
//            SUPPORTED.add(Year.class);
//            SUPPORTED.add(YearMonth.class);
//            SUPPORTED.add(ZonedDateTime.class);

            // some random JDK classes that may be used in map keys
            // Class, Package and Module supported as simplifiers
//            SUPPORTED.add(Class.class);
//            SUPPORTED.add(Package.class);
//            SUPPORTED.add(Module.class);
            // supported as simplifier
//            KNOWN_TO_STRING.add(URI.class);
//            KNOWN_TO_STRING.add(URL.class);
//            SUPPORTED.add(Calendar.class);
            // 2 types of InetAddresses supported via simplifiers
//            KNOWN_TO_STRING.add(Inet4Address.class);
//            KNOWN_TO_STRING.add(Inet6Address.class);
            KNOWN_TO_STRING.add(Locale.class);
            KNOWN_TO_STRING.add(UUID.class);
        }

        private DateFormatConfiguration dateFormatConfiguration;

        public StringParser() {
            this.dateFormatConfiguration = new DateFormatConfiguration();
        }

        @Override
        public void setDateFormatConfiguration(DateFormatConfiguration configuration) {
            this.dateFormatConfiguration = configuration;
        }

        public void setStringConverters(Map<Class<?>, ObjectAccessor> accessors) {
            ACCESSORS.putAll(accessors);
            ALL_ACCESSORS.putAll(accessors);
        }

        @Override
        public Object parse(String pValue) {
            return pValue;
        }

        @Override
        public Object parse(Object pValue) {
            // first with dedicated accessor
            ObjectAccessor accessor = ALL_ACCESSORS.get(pValue.getClass());
            // we assume it supports conversion to String - otherwise we'd not get it via setStringConverters
            if (accessor != null) {
                return accessor.extractString(pValue);
            }

            // last - toString() where we know it's fine
            if (KNOWN_TO_STRING.contains(pValue.getClass())) {
                return pValue.toString();
            }

            throw new IllegalArgumentException("Can't convert " + pValue.getClass().getName() + " to String");
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            if (KNOWN_TO_STRING.contains(pValueClass) || ALL_ACCESSORS.containsKey(pValueClass)) {
                return true;
            }
            // org.jolokia.service.serializer.json.ObjectAccessor.extractString() checking
            for (Map.Entry<Class<?>, ObjectAccessor> entry : ACCESSORS.entrySet()) {
                Class<?> c = entry.getKey();
                ObjectAccessor accessor = entry.getValue();
                if (c.isAssignableFrom(pValueClass)) {
                    ALL_ACCESSORS.put(pValueClass, accessor);
                    return true;
                }
            }
            // toString() checking
            if (!UNDESIRED_TO_STRING.contains(pValueClass)) {
                try {
                    Method toString = pValueClass.getMethod("toString");
                    if (toString.getDeclaringClass() != Object.class) {
                        KNOWN_TO_STRING.add(pValueClass);
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }

            return false;
        }
    }

    private static class BigDecimalParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return new BigDecimal(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            Number n = (Number) pValue;
            if (n instanceof Float floatNumber) {
                // #934 we omit NaN/infinity instead of throwing NumberFormatException
                if (!Float.isFinite(floatNumber)) {
                    return null;
                }
                // BigDecimal(float) gives different (worse!) result than BigDecimal(float.toString())...
//                return new BigDecimal(floatNumber);
                return new BigDecimal(Float.toString(floatNumber));
            }
            if (n instanceof Double doubleNumber) {
                // #934 we omit NaN/infinity instead of throwing NumberFormatException
                if (!Double.isFinite(doubleNumber)) {
                    return null;
                }
//                return new BigDecimal(doubleNumber);
                return new BigDecimal(Double.toString(doubleNumber));
            }
            return new BigDecimal(n.toString());
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return pValue instanceof Number;
        }
    }

    private static class BigIntegerParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return new BigInteger(pValue);
        }

        @Override
        public Object parse(Object pValue) {
            return new BigInteger(pValue.toString());
        }

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return pValue instanceof Number && !(pValue instanceof BigDecimal) && !(pValue instanceof Double) && !(pValue instanceof Float);
        }
    }

    private static class DateParser implements Parser, DateFormatConfigurationAware {
        private DateFormatConfiguration dateFormatConfiguration;

        @Override
        public void setDateFormatConfiguration(DateFormatConfiguration configuration) {
            this.dateFormatConfiguration = configuration;
        }

        @Override
        public Object parse(String pValue) {
            try {
                if (dateFormatConfiguration.usesUnixTime()) {
                    Long v = Long.parseLong(pValue);
                    return dateFormatConfiguration.unixTimeToDate(v);
                } else {
                    // still, user (or test case) may have sent long value despite the configuration
                    // but we have to assume it's millis
                    try {
                        return dateFormatConfiguration.unixTimeInMillisToDate(Long.parseLong(pValue));
                    } catch (NumberFormatException e) {
                        // we expect value according to configured format
                        return dateFormatConfiguration.parseAsDate(pValue);
                    }
                }
            } catch (NumberFormatException | java.text.ParseException exp) {
                // this is fallback after expecting long or proper format
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

        @Override
        public boolean supports(Class<?> pValueClass, Object pValue) {
            return JSONObject.class == pValueClass && pValue instanceof JSONObject json && json.size() == 1 && json.containsKey("objectName");
        }

        @Override
        public Object parse(Object pValue) {
            // reverse of org.jolokia.converter.json.simplifier.ObjectNameSimplifier
            if (pValue instanceof JSONObject json && json.get("objectName") instanceof String name) {
                try {
                    return ObjectName.getInstance(name);
                } catch (MalformedObjectNameException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }

            return Parser.super.parse(pValue);
        }
    }

    private static class URIParser implements Parser {
        @Override
        public Object parse(String pValue) {
            return URI.create(pValue);
        }
    }

    private static class URLParser implements Parser {
        @Override
        public Object parse(String pValue) {
            try {
                return new URL(pValue);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Cannot parse URL " + pValue + ": " + e.getMessage(), e);
            }
        }
    }

    private static class CalendarParser implements Parser, DateFormatConfigurationAware {
        private DateFormatConfiguration dateFormatConfiguration;

        @Override
        public void setDateFormatConfiguration(DateFormatConfiguration configuration) {
            this.dateFormatConfiguration = configuration;
        }

        @Override
        public Object parse(String pValue) {
            try {
                if (dateFormatConfiguration.usesUnixTime()) {
                    Long v = Long.parseLong(pValue);
                    Date date = dateFormatConfiguration.unixTimeToDate(v);
                    Calendar result = Calendar.getInstance();
                    result.setTime(date);
                    return result;
                } else {
                    // still, user (or test case) may have sent long value despite the configuration
                    // but we have to assume it's millis
                    try {
                        Date date = dateFormatConfiguration.unixTimeInMillisToDate(Long.parseLong(pValue));
                        Calendar result = Calendar.getInstance();
                        result.setTime(date);
                        return result;
                    } catch (NumberFormatException e) {
                        // we expect value according to configured format
                        Date date = dateFormatConfiguration.parseAsDate(pValue);
                        Calendar result = Calendar.getInstance();
                        result.setTime(date);
                        return result;
                    }
                }
            } catch (NumberFormatException | java.text.ParseException exp) {
                // this is fallback after expecting long or proper format
                Date date = DateUtil.fromISO8601(pValue);
                Calendar result = Calendar.getInstance();
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
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e.getMessage(), e);
            }
        }
    }

    private static class TemporalParser implements DateFormatConfigurationAware {
        private DateFormatConfiguration dateFormatConfiguration;

        @Override
        public void setDateFormatConfiguration(DateFormatConfiguration configuration) {
            this.dateFormatConfiguration = configuration;
        }

        public Object extract(Class<?> temporalType, String pValue) {
            try {
                if (dateFormatConfiguration.usesUnixTime()) {
                    Long v = Long.parseLong(pValue);
                    return dateFormatConfiguration.unixTimeToTemporal(temporalType, v);
                } else {
                    // still, user (or test case) may have sent long value despite the configuration,
                    // but we have to assume it's millis
                    try {
                        return dateFormatConfiguration.unixTimeInNanosToTemporal(temporalType, Long.parseLong(pValue));
                    } catch (NumberFormatException e) {
                        // we expect value according to configured format
                        return dateFormatConfiguration.parseAsTemporal(temporalType, pValue);
                    }
                }
            } catch (NumberFormatException exp) {
                throw new IllegalArgumentException("Cannot create " + temporalType + " from \"" + pValue + "\"");
            }
        }

        public Object extract(Class<?> temporalType, Object pValue) {
            if (pValue instanceof Number numberValue) {
                if (dateFormatConfiguration.usesUnixTime()) {
                    return dateFormatConfiguration.unixTimeToTemporal(temporalType, numberValue.longValue());
                } else {
                    // despite the configuration, user has sent Long value, so let's convert from nanos
                    // which is the default (for now) behavior for jolokia-client-java
                    return dateFormatConfiguration.unixTimeInNanosToTemporal(temporalType, numberValue.longValue());
                }
            } else if (pValue instanceof String stringValue) {
                return extract(temporalType, stringValue);
            }

            throw new IllegalArgumentException("Cannot handle Temporal of class \"" + temporalType + "\" for value " + pValue);
        }

        public boolean supports(Class<?> pValueClass, Object pValue) {
            return noIntegerOverflow(pValue, Long.class, 63, Long.MIN_VALUE, Long.MAX_VALUE);
        }
    }

}
