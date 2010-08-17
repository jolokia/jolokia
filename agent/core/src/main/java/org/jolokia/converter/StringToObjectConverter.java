package org.jolokia.converter;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
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

    public Object convertFromString(String pType, String pValue) {
        // TODO: Look for an external solution or support more types
        if ("[null]".equals(pValue)) {
            return null;
        }
        if (pType.startsWith("[") && pType.length() >= 2) {
            return convertToArray(pType, pValue);
        }

        // Special string value
        if ("\"\"".equals(pValue)) {
            if (matchesType(pType,String.class)) {
                return "";
            }
            throw new IllegalArgumentException("Cannot convert empty string tag to type " + pType);
        }

        Extractor extractor = EXTRACTOR_MAP.get(pType);
        if (extractor == null) {
            throw new IllegalArgumentException(
                    "Cannot convert string " + pValue + " to type " +
                            pType + " because no converter could be found");
        }
        return extractor.extract(pValue);
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

    private boolean matchesType(String pType, Class pClass) {
        return pClass.getName().equals(pType);
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
