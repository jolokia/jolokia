package org.jolokia.converter;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.jolokia.util.ClassUtil;
import org.jolokia.util.DateUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
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
        PARSER_MAP.put(Boolean.class.getName(),new BooleanParser());
        PARSER_MAP.put("boolean",new BooleanParser());
        PARSER_MAP.put("char",new CharParser());
        PARSER_MAP.put(String.class.getName(),new StringParser());
        PARSER_MAP.put(Date.class.getName(),new DateParser());

        JSONParser jsonExtractor = new JSONParser();
        PARSER_MAP.put(JSONObject.class.getName(), jsonExtractor);
        PARSER_MAP.put(JSONArray.class.getName(), jsonExtractor);

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
     *
     * @param pExpectedClassName type name of the expected type
     * @param pValue value to either take directly or to convert from its string representation.
     * @return the prepared / converted object
     */
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
     * Handle conversion for OpenTypes. The value is expected to be in JSON.
     *  
     * @param openType
     * @param pValue
     * @return
     */
	public Object prepareValue(OpenType<?> openType, Object pValue) {
		if (openType instanceof SimpleType) {
			// convert the simple type using prepareValue
			SimpleType<?> sType = (SimpleType<?>) openType;
			String className = sType.getClassName();
			return prepareValue(className, pValue);
			
		} else if (openType instanceof ArrayType<?>) {
			ArrayType<?> aType = (ArrayType<?>) openType;
			// prepare each value in the array and then process the array of values
			
			Object jsonValue = prepareValue(JSONObject.class.getName(), pValue);
			if (jsonValue instanceof JSONArray) {
				Collection<?> jsonArray = (Collection<?>) jsonValue;
				OpenType<?> elementOpenType = aType.getElementOpenType();
				Object[] valueArray = createTargetArray(aType, jsonArray.size());
				
				Iterator<?> it = jsonArray.iterator();
				for (int i = 0; i < valueArray.length ; ++i) {
					Object element = it.next();
					Object elementValue = prepareValue(elementOpenType, element.toString());
					valueArray[i] = elementValue;
				}
				
				return valueArray;
				
			} else {
				throw new IllegalArgumentException(
						"Cannot convert string " + pValue + " to type " +
	                    openType + " because unsupported JSON object type: " + jsonValue);				
			}
			
		} else if (openType instanceof CompositeType) {
			// break down the composite type to its field and recurse for converting each field
			Object jsonValue = prepareValue(JSONObject.class.getName(), pValue);
			if (jsonValue instanceof JSONObject) {
				@SuppressWarnings("unchecked")
				Map<String, Object> jsonObj = (HashMap<String, Object>) jsonValue;
				Map<String, Object> itemValues = new HashMap<String, Object>();
				CompositeType cType = (CompositeType) openType;	
				
				for (String itemName: jsonObj.keySet()) {
					if (!cType.containsKey(itemName)) {
						throw new IllegalArgumentException(
								"Cannot convert string " + pValue + " to type " +
			                    openType + " because of unknown key: " + itemName);				
					}
					Object itemValue = jsonObj.get(itemName);
					if (itemValue != null) {
						OpenType<?> itemType = cType.getType(itemName);
						
						Object convertedValue = prepareValue(itemType, itemValue);
						itemValues.put(itemName, convertedValue);
					}
				}
				
				/* fields that were not given in the JSON must be added with 
				 * null for Objects and the default value for primitives 
				 */
				for (String itemName : cType.keySet()) {
					if (!itemValues.containsKey(itemName)) {
						Object itemValue = null;
						OpenType<?> itemType = cType.getType(itemName);
						if (itemType instanceof SimpleType) {
							SimpleType<?> sType = (SimpleType<?>) itemType;												
							itemValue = DefaultValues.getDefaultValue(sType.getClassName());
						}
						itemValues.put(itemName, itemValue);
					}
				}
				
				try {
					CompositeDataSupport cData = new CompositeDataSupport(cType, itemValues);
					return cData;
					
				} catch (OpenDataException e) {
					throw new IllegalArgumentException(
							"Cannot convert string " + pValue + " to type " +
		                    openType + " because unsupported JSON data: " + e.getMessage());				
				}
				
			} else {
				throw new IllegalArgumentException(
						"Cannot convert string " + pValue + " to type " +
	                    openType + " because unsupported JSON object type: " + jsonValue);				
			}
			
		} else if (openType instanceof TabularType) {
			TabularType tType = (TabularType) openType;
			CompositeType rowType = tType.getRowType();
			if (rowType.keySet().size() != 2 || !rowType.containsKey("key") || !rowType.containsKey("value")) {
				throw new IllegalArgumentException(
						"Cannot convert string " + pValue + " to type " +
	                    openType + " because the TabularData can't be converted: " + tType);				
			}
			
			TabularDataSupport tabularData = new TabularDataSupport(tType);
			
			Object jsonValue = prepareValue(JSONObject.class.getName(), pValue);
			if (jsonValue instanceof JSONObject) {
				@SuppressWarnings("unchecked")
				Map<String, String> jsonObj = (Map<String,String>) jsonValue;
				
				for(Map.Entry<String, String> entry : jsonObj.entrySet()) {
					Map<String, Object> map = new HashMap<String, Object>();
					Object key = prepareValue(rowType.getType("key"), entry.getKey());
					map.put("key", key);
					
					Object value = prepareValue(rowType.getType("value"), entry.getValue());
					map.put("value", value);

					try {
						CompositeData compositeData = new CompositeDataSupport(rowType, map);
						tabularData.put(compositeData);
						
					} catch (OpenDataException e) {					
						throw new IllegalArgumentException(e.getMessage());
					}
				}				
				
				return tabularData;
				
			} else {
				throw new IllegalArgumentException(						
						"Cannot convert string " + pValue + " to type " +
	                    openType + " because the JSON data doesn't represent a list: " + jsonValue);				
			}
			
			
		} else {
			throw new IllegalArgumentException(
					"Cannot convert string " + pValue + " to type " +
                    openType + " because no converter could be found");
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
    private Object prepareForDirectUsage(String pExpectedClassName, Object pArgument) {
        Class expectedClass = ClassUtil.classForName(pExpectedClassName);
        if (expectedClass == null) {
            // It is probably a native type, so we let happen the string conversion
            // later on (e.g. conversion of pArgument.toString()) which will throw
            // an exception at this point if conversion can not be done
            return null;
        }
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
    Object convertFromString(String pType, String pValue) {
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
        String[] values = split(pValue);
        Object ret = Array.newInstance(valueType,values.length);
        int i = 0;
        for (String value : values) {
            Array.set(ret,i++,value.equals("[null]") ? null : convertFromString(valueType.getCanonicalName(),value));
        }
        return ret;
    }

    // Convert a list to an array of the given type
    private Object convertListToArray(Class pType, List pList) {
        List argAsList = (List) pList;
        Class valueType = pType.getComponentType();
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
    }


    private String[] split(String pValue) {
        // For now, split simply on ','. This is very simplistic
        // and will fail on complex strings containing commas as content.
        // Use a full blown CSV parser then (but only for string)
        return pValue.split("\\s*,\\s*");
    }

    
    private Object[] createTargetArray(ArrayType<?> aType, int length) {
		OpenType<?> elementOpenType = aType.getElementOpenType();
		Object[] valueArray;
		if (elementOpenType instanceof SimpleType) {
			SimpleType<?> sElementType = (SimpleType<?>) elementOpenType;
			Class<?> elementClass;
			try {
				elementClass = Class.forName(sElementType.getClassName());
				valueArray = (Object[]) Array.newInstance(elementClass, length);

			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("Can't instantiate array: " + e.getMessage());
			}
			
		} else if (elementOpenType instanceof CompositeType) {
			valueArray = new CompositeData[length];
			
		} else {
			throw new IllegalArgumentException("Unsupported array element type: " + elementOpenType);
		}

		return valueArray;
    }
    
    // ===========================================================================
    // Extractor interface
    private interface Parser {
        Object extract(String pValue);
    }

    private static class StringParser implements Parser {
        public Object extract(String pValue) { return pValue; }
    }
    private static class IntParser implements Parser {
        public Object extract(String pValue) { return Integer.parseInt(pValue); }
    }
    private static class LongParser implements Parser {
        public Object extract(String pValue) { return Long.parseLong(pValue); }
    }
    private static class BooleanParser implements Parser {
        public Object extract(String pValue) { return Boolean.parseBoolean(pValue); }
    }
    private static class DoubleParser implements Parser {
        public Object extract(String pValue) { return Double.parseDouble(pValue); }
    }
    private static class FloatParser implements Parser {
        public Object extract(String pValue) { return Float.parseFloat(pValue); }
    }
    private static class ByteParser implements Parser {
        public Object extract(String pValue) { return Byte.parseByte(pValue); }
    }
    private static class CharParser implements Parser {
        public Object extract(String pValue) { return pValue.charAt(0); }
    }
    private static class ShortParser implements Parser {
        public Object extract(String pValue) { return Short.parseShort(pValue); }
    }

    private static class DateParser implements Parser {
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
        public Object extract(String pValue) {
            try {
                return new org.json.simple.parser.JSONParser().parse(pValue);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e,e);
            }
        }
    }
    
    /**
     * Map default values for primitive wrapper classes 
     */
    public static class DefaultValues {
        private static boolean DEFAULT_BOOLEAN;
        private static byte DEFAULT_BYTE;
        private static byte DEFAULT_CHAR;
        private static short DEFAULT_SHORT;
        private static int DEFAULT_INT;
        private static long DEFAULT_LONG;
        private static float DEFAULT_FLOAT;
        private static double DEFAULT_DOUBLE;
        
        private static Map<String, Object> defaultValues;
        
        static {
        	defaultValues = new HashMap<String, Object>();
            defaultValues.put(Boolean.class.getName(), new Boolean(DEFAULT_BOOLEAN));
            defaultValues.put(Byte.class.getName(), new Byte(DEFAULT_BYTE));
            defaultValues.put(Character.class.getName(), new Short(DEFAULT_CHAR));
            defaultValues.put(Short.class.getName(), new Short(DEFAULT_SHORT));
            defaultValues.put(Integer.class.getName(), new Integer(DEFAULT_INT));
            defaultValues.put(Long.class.getName(), new Long(DEFAULT_LONG));
            defaultValues.put(Float.class.getName(), new Float(DEFAULT_FLOAT));
            defaultValues.put(Double.class.getName(), new Double(DEFAULT_DOUBLE));        	
        }

        public static Object getDefaultValue(String className) {
        	return defaultValues.get(className);
        }
    }

}
