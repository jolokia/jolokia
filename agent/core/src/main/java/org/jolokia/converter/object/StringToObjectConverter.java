package org.jolokia.converter.object;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import javax.management.ObjectName;

import org.jolokia.util.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;


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


/**
 * Converter from a string representation to its Java object form
 * @author roland
 * @since Jun 11, 2009
 */
public class StringToObjectConverter {


    private static final Map<String,Parser> PARSER_MAP = new HashMap<String,Parser>();
    private static final Map<String,Class> TYPE_SIGNATURE_MAP = new HashMap<String, Class>();

    static {
        PARSER_MAP.put(Byte.class.getName(),new ByteParser());
        PARSER_MAP.put("byte",new ByteParser());
        PARSER_MAP.put(Integer.class.getName(),new IntParser());
        PARSER_MAP.put("int",new IntParser());
        PARSER_MAP.put(Long.class.getName(),new LongParser());
        PARSER_MAP.put("long",new LongParser());
        PARSER_MAP.put(Short.class.getName(),new ShortParser());
        PARSER_MAP.put("short",new ShortParser());
        PARSER_MAP.put(Double.class.getName(),new DoubleParser());
        PARSER_MAP.put("double",new DoubleParser());
        PARSER_MAP.put(Float.class.getName(),new FloatParser());
        PARSER_MAP.put("float",new FloatParser());
        PARSER_MAP.put(BigDecimal.class.getName(),new BigDecimalParser());
        PARSER_MAP.put(BigInteger.class.getName(),new BigIntegerParser());

        PARSER_MAP.put(Boolean.class.getName(),new BooleanParser());
        PARSER_MAP.put("boolean",new BooleanParser());
        PARSER_MAP.put("char",new CharParser());
        PARSER_MAP.put(Character.class.getName(),new CharParser());
        PARSER_MAP.put(String.class.getName(),new StringParser());
        PARSER_MAP.put(Date.class.getName(),new DateParser());
        PARSER_MAP.put(ObjectName.class.getName(), new ObjectNameParser());

        JSONParser jsonExtractor = new JSONParser();
        for (Class type : new Class[] { Map.class, List.class,
                                        JSONObject.class, JSONArray.class }) {
            PARSER_MAP.put(type.getName(),jsonExtractor);
        }

        TYPE_SIGNATURE_MAP.put("Z",boolean.class);
        TYPE_SIGNATURE_MAP.put("B",byte.class);
        TYPE_SIGNATURE_MAP.put("C",char.class);
        TYPE_SIGNATURE_MAP.put("S",short.class);
        TYPE_SIGNATURE_MAP.put("I",int.class);
        TYPE_SIGNATURE_MAP.put("J",long.class);
        TYPE_SIGNATURE_MAP.put("F",float.class);
        TYPE_SIGNATURE_MAP.put("D",double.class);
    }

    /**
     * Prepare a value from a either a given object or its string representation.
     * If the value is already assignable to the given class name it is returned directly.
     *
     * @param pExpectedClassName type name of the expected type
     * @param pValue value to either take directly or to convert from its string representation.
     * @return the prepared / converted object
     */
    public Object prepareValue(String pExpectedClassName, Object pValue) {
        if (pValue == null) {
            return null;
        } else {
            Class expectedClass = ClassUtil.classForName(pExpectedClassName);
            Object param = null;
            if (expectedClass != null) {
                param = prepareValue(expectedClass,pValue);
            }
            if (param == null) {
                // Ok, we try to convert it from a string
                // If expectedClass is null, it is probably a native type, so we
                // let happen the string conversion
                // later on (e.g. conversion of pArgument.toString()) which will throw
                // an exception at this point if conversion can not be done

                return convertFromString(pExpectedClassName, pValue.toString());
            }
            return param;
        }
    }

    // Extract a type version of the method above. This might be useful later
    // on, e.g. when setting enums should be supported for certain
    // use cases
    private Object prepareValue(Class expectedClass, Object pValue) {
        if (pValue == null) {
            return null;
        }
        if (Enum.class.isAssignableFrom(expectedClass)) {
            return Enum.valueOf(expectedClass,pValue.toString());
        } else {
            return prepareForDirectUsage(expectedClass, pValue);
        }
    }

    /**
     * For GET requests, where operation arguments and values to write are given in
     * string representation as part of the URL, certain special tags are used to indicate
     * special values:
     *
     * <ul>
     *    <li><code>[null]</code> for indicating a null value</li>
     *    <li><code>""</code> for indicating an empty string</li>
     * </ul>
     *
     * This method converts these tags to the proper value. If not a tag, the original
     * value is returned.
     *
     * If you need this tag values in the original semantics, please use POST requests.
     *
     * @param pValue the string value to check for a tag
     * @return the converted value or the original one if no tag has been found.
     */
    public static String convertSpecialStringTags(String pValue) {
        if ("[null]".equals(pValue)) {
            // Null marker for get requests
            return null;
        } else if ("\"\"".equals(pValue)) {
            // Special string value for an empty String
            return "";
        } else {
            return pValue;
        }
    }

    // ======================================================================================================

    // Check whether an argument can be used directly or whether it needs some sort
    // of conversion. Returns null if a string conversion should happen
    private Object prepareForDirectUsage(Class expectedClass, Object pArgument) {
        Class givenClass = pArgument.getClass();
        if (expectedClass.isArray() && List.class.isAssignableFrom(givenClass)) {
            return convertListToArray(expectedClass, (List) pArgument);
        } else {
            return expectedClass.isAssignableFrom(givenClass) ? pArgument : null;
        }
    }

    /**
     * Deserialize a string representation to an object for a given type
     *
     * @param pType type to convert to
     * @param pValue the value to convert from
     * @return the converted value
     */
    public Object convertFromString(String pType, String pValue) {
        String value = convertSpecialStringTags(pValue);

        if (value == null) {
            return null;
        }
        if (pType.startsWith("[") && pType.length() >= 2) {
            return convertToArray(pType, value);
        }

        Parser parser = PARSER_MAP.get(pType);
        if (parser == null) {
            throw new IllegalArgumentException(
                    "Cannot convert string " + value + " to type " +
                            pType + " because no converter could be found");
        }
        return parser.extract(value);
    }

    // Convert an array
    private Object convertToArray(String pType, String pValue) {
        // It's an array
        String t = pType.substring(1,2);
        Class valueType;
        if (t.equals("L")) {
            // It's an object-type
            String oType = pType.substring(2,pType.length()-1).replace('/','.');
            valueType = ClassUtil.classForName(oType);
            if (valueType == null) {
                throw new IllegalArgumentException("No class of type " + oType + "found");
            }
        } else {
            valueType = TYPE_SIGNATURE_MAP.get(t);
            if (valueType == null) {
                throw new IllegalArgumentException("Cannot convert to unknown array type " + t);
            }
        }
        String[] values = EscapeUtil.splitAsArray(pValue, EscapeUtil.PATH_ESCAPE, ",");
        Object ret = Array.newInstance(valueType,values.length);
        int i = 0;
        for (String value : values) {
            Array.set(ret,i++,value.equals("[null]") ? null : convertFromString(valueType.getCanonicalName(),value));
        }
        return ret;
    }

    // Convert a list to an array of the given type
    private Object convertListToArray(Class pType, List pList) {
        Class valueType = pType.getComponentType();
        Object ret = Array.newInstance(valueType, pList.size());
        int i = 0;
        for (Object value : pList) {
            if (value == null) {
                if (!valueType.isPrimitive()) {
                    Array.set(ret,i++,null);
                } else {
                    throw new IllegalArgumentException("Cannot use a null value in an array of type " + valueType.getSimpleName());
                }
            } else {
                if (valueType.isAssignableFrom(value.getClass())) {
                    // Can be set directly
                    Array.set(ret,i++,value);
                } else {
                    // Try to convert from string
                    Array.set(ret,i++,convertFromString(valueType.getCanonicalName(), value.toString()));
                }
            }
        }
        return ret;
    }



    // ===========================================================================
    // Extractor interface
    private interface Parser {
        /**
         * Extract a particular string value
         * @param pValue value to extract
         * @return the extracted value
         */
        Object extract(String pValue);
    }

    private static class StringParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return pValue; }
    }
    private static class IntParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Integer.parseInt(pValue); }
    }
    private static class LongParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Long.parseLong(pValue); }
    }
    private static class BooleanParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Boolean.parseBoolean(pValue); }
    }
    private static class DoubleParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Double.parseDouble(pValue); }
    }
    private static class FloatParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Float.parseFloat(pValue); }
    }
    private static class ByteParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Byte.parseByte(pValue); }
    }
    private static class CharParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return pValue.charAt(0); }
    }
    private static class ShortParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return Short.parseShort(pValue); }
    }

    private static class BigDecimalParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return new BigDecimal(pValue); }
    }

    private static class BigIntegerParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) { return new BigInteger(pValue); }
    }

    private static class DateParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) {
            long time;
            try {
                time = Long.parseLong(pValue);
                return new Date(time);
            } catch (NumberFormatException exp) {
                return DateUtil.fromISO8601(pValue);
            }
        }
    }

    private static class JSONParser implements Parser {
        /** {@inheritDoc} */
        public Object extract(String pValue) {
            try {
                return new org.json.simple.parser.JSONParser().parse(pValue);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e,e);
            }
        }
    }

    private static class ObjectNameParser implements Parser {
    	/** {@inheritDoc} */
    	public Object extract(String pValue) {
    		try {
    			return new javax.management.ObjectName(pValue);
    		} catch(javax.management.MalformedObjectNameException e) {
    			throw new IllegalArgumentException("Cannot parse ObjectName "+ pValue +": " +e, e);
    		}
    	}
    }

}
