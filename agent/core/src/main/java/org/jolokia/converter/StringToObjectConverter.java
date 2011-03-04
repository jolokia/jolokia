package org.jolokia.converter;

import java.lang.reflect.Array;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/*
 *  Copyright 2009-2010 Roland Huss
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


/**
 * @author roland
 * @since Jun 11, 2009
 */
public class StringToObjectConverter {


    private static final Map<String,Extractor> EXTRACTOR_MAP = new HashMap<String,Extractor>();
    private static final Map<String,Class> TYPE_SIGNATURE_MAP = new HashMap<String, Class>();

    static {
        EXTRACTOR_MAP.put(Byte.class.getName(),new ByteExtractor());
        EXTRACTOR_MAP.put("byte",new ByteExtractor());
        EXTRACTOR_MAP.put(Integer.class.getName(),new IntExtractor());
        EXTRACTOR_MAP.put("int",new IntExtractor());
        EXTRACTOR_MAP.put(Long.class.getName(),new LongExtractor());
        EXTRACTOR_MAP.put("long",new LongExtractor());
        EXTRACTOR_MAP.put(Short.class.getName(),new ShortExtractor());
        EXTRACTOR_MAP.put("short",new ShortExtractor());
        EXTRACTOR_MAP.put(Double.class.getName(),new DoubleExtractor());
        EXTRACTOR_MAP.put("double",new DoubleExtractor());
        EXTRACTOR_MAP.put(Float.class.getName(),new FloatExtractor());
        EXTRACTOR_MAP.put("float",new FloatExtractor());
        EXTRACTOR_MAP.put(Boolean.class.getName(),new BooleanExtractor());
        EXTRACTOR_MAP.put("boolean",new BooleanExtractor());
        EXTRACTOR_MAP.put("char",new CharExtractor());
        EXTRACTOR_MAP.put(String.class.getName(),new StringExtractor());

        JSONExtractor jsonExtractor = new JSONExtractor();
        EXTRACTOR_MAP.put(JSONObject.class.getName(), jsonExtractor);
        EXTRACTOR_MAP.put(JSONArray.class.getName(), jsonExtractor);

        TYPE_SIGNATURE_MAP.put("Z",boolean.class);
        TYPE_SIGNATURE_MAP.put("B",byte.class);
        TYPE_SIGNATURE_MAP.put("C",char.class);
        TYPE_SIGNATURE_MAP.put("S",short.class);
        TYPE_SIGNATURE_MAP.put("I",int.class);
        TYPE_SIGNATURE_MAP.put("J",long.class);
        TYPE_SIGNATURE_MAP.put("F",float.class);
        TYPE_SIGNATURE_MAP.put("D",double.class);
    }

    public Object prepareValue(String pExpectedClassName, Object pValue) {
        if (pValue == null) {
            return null;
        } else {
            Object param = prepareForDirectUsage(pExpectedClassName, pValue);
            if (param == null) {
                // Ok, we try to convert it from a string
                return convertFromString(pExpectedClassName, pValue.toString());
            }
            return param;
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
    // of conversion
    private Object prepareForDirectUsage(String pExpectedClassName, Object pArgument) {
        try {
            Class expectedClass = Class.forName(pExpectedClassName,true,Thread.currentThread().getContextClassLoader());
            Class givenClass = pArgument.getClass();
            if (expectedClass.isArray() && List.class.isAssignableFrom(givenClass)) {
                List argAsList = (List) pArgument;
                Class valueType = expectedClass.getComponentType();
                Object ret = Array.newInstance(valueType, argAsList.size());
                int i = 0;
                for (Object value : argAsList) {
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
            } else {
                return expectedClass.isAssignableFrom(givenClass) ? pArgument : null;
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Deserialize a string representation to an object for a given type
     *
     * @param pType type to convert to
     * @param pValue the value to convert from
     * @return the converted value
     */
    Object convertFromString(String pType, String pValue) {
        String value = convertSpecialStringTags(pValue);

        if (value == null) {
            return null;
        }
        if (pType.startsWith("[") && pType.length() >= 2) {
            return convertToArray(pType, value);
        }

        Extractor extractor = EXTRACTOR_MAP.get(pType);
        if (extractor == null) {
            throw new IllegalArgumentException(
                    "Cannot convert string " + value + " to type " +
                            pType + " because no converter could be found");
        }
        return extractor.extract(value);
    }


    // Convert an array
    private Object convertToArray(String pType, String pValue) {
        // It's an array
        String t = pType.substring(1,2);
        Class valueType;
        if (t.equals("L")) {
            // It's an object-type
            String oType = pType.substring(2,pType.length()-1).replace('/','.');
            try {
                valueType = Class.forName(oType,true,Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("No class of type " + oType + "found: " + e,e);
            }
        } else {
            valueType = TYPE_SIGNATURE_MAP.get(t);
            if (valueType == null) {
                throw new IllegalArgumentException("Cannot convert to unknown array type " + t);
            }
        }
        String[] values = split(pValue);
        Object ret = Array.newInstance(valueType,values.length);
        int i = 0;
        for (String value : values) {
            Array.set(ret,i++,value.equals("[null]") ? null : convertFromString(valueType.getCanonicalName(),value));
        }
        return ret;
    }

    private String[] split(String pValue) {
        // For now, split simply on ','. This is very simplistic
        // and will fail on complex strings containing commas as content.
        // Use a full blown CSV parser then (but only for string)
        return pValue.split("\\s*,\\s*");
    }

    // ===========================================================================
    // Extractor interface
    private interface Extractor {
        Object extract(String pValue);
    }

    private static class StringExtractor implements Extractor {
        public Object extract(String pValue) { return pValue; }
    }
    private static class IntExtractor implements Extractor {
        public Object extract(String pValue) { return Integer.parseInt(pValue); }
    }
    private static class LongExtractor implements Extractor {
        public Object extract(String pValue) { return Long.parseLong(pValue); }
    }
    private static class BooleanExtractor implements Extractor {
        public Object extract(String pValue) { return Boolean.parseBoolean(pValue); }
    }
    private static class DoubleExtractor implements Extractor {
        public Object extract(String pValue) { return Double.parseDouble(pValue); }
    }
    private static class FloatExtractor implements Extractor {
        public Object extract(String pValue) { return Float.parseFloat(pValue); }
    }
    private static class ByteExtractor implements Extractor {
        public Object extract(String pValue) { return Byte.parseByte(pValue); }
    }
    private static class CharExtractor implements Extractor {
        public Object extract(String pValue) { return pValue.charAt(0); }
    }
    private static class ShortExtractor implements Extractor {
        public Object extract(String pValue) { return Short.parseShort(pValue); }
    }

    private static class JSONExtractor implements Extractor {
        public Object extract(String pValue) {
            try {
                return new JSONParser().parse(pValue);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e,e);
            }
        }
    }
}
