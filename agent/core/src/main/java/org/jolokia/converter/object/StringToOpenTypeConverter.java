package org.jolokia.converter.object;

import java.lang.reflect.Array;
import java.util.*;

import javax.management.openmbean.*;

import org.json.simple.*;

/**
 * Converter which converts an string or JSON representation to
 * an object represented by an {@link OpenType}.
 *
 * @author Assaf Berg, roland
 * @since 02.08.11
 */
public class StringToOpenTypeConverter {

    private StringToObjectConverter stringToObjectConverter;


    /**
     * Constructor
     *
     * @param pStringToObjectConverter converter for the 'leaf' values.
     */
    public StringToOpenTypeConverter(StringToObjectConverter pStringToObjectConverter) {
        stringToObjectConverter = pStringToObjectConverter;
    }

    /**
     * Handle conversion for OpenTypes. The value is expected to be in JSON (either
     * an {@link JSONAware} object or its string representation
     *
     * @param openType target type
     * @param pValue value to convert from
     * @return the value converted
     */
	public Object convertToObject(OpenType<?> openType, Object pValue) {
		if (openType instanceof SimpleType) {
			// convert the simple type using prepareValue
			SimpleType<?> sType = (SimpleType<?>) openType;
			String className = sType.getClassName();
			return stringToObjectConverter.prepareValue(className, pValue);

		} else if (openType instanceof ArrayType<?>) {
			ArrayType<?> aType = (ArrayType<?>) openType;
			// prepare each value in the array and then process the array of values
			Object jsonValue = stringToObjectConverter.prepareValue(JSONArray.class, pValue);
			if (jsonValue instanceof JSONArray) {
				Collection<?> jsonArray = (Collection<?>) jsonValue;
                OpenType<?> elementOpenType = aType.getElementOpenType();
				Object[] valueArray = createTargetArray(aType, jsonArray.size());

				Iterator<?> it = jsonArray.iterator();
				for (int i = 0; i < valueArray.length ; ++i) {
					Object element = it.next();
                    Object elementValue = convertToObject(elementOpenType, element);
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
			Object jsonValue = stringToObjectConverter.prepareValue(JSONObject.class, pValue);
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

						Object convertedValue = convertToObject(itemType, itemValue);
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

			Object jsonValue = stringToObjectConverter.prepareValue(JSONObject.class, pValue);
			if (jsonValue instanceof JSONObject) {
				@SuppressWarnings("unchecked")
				Map<String, String> jsonObj = (Map<String,String>) jsonValue;

				for(Map.Entry<String, String> entry : jsonObj.entrySet()) {
					Map<String, Object> map = new HashMap<String, Object>();
					Object key = convertToObject(rowType.getType("key"), entry.getKey());
					map.put("key", key);

					Object value = convertToObject(rowType.getType("value"), entry.getValue());
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


    /**
     * Map default values for primitive wrapper classes
     */
    private static class DefaultValues {
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
            defaultValues.put(Boolean.class.getName(), DEFAULT_BOOLEAN);
            defaultValues.put(Byte.class.getName(), DEFAULT_BYTE);
            defaultValues.put(Character.class.getName(), (short) DEFAULT_CHAR);
            defaultValues.put(Short.class.getName(), DEFAULT_SHORT);
            defaultValues.put(Integer.class.getName(), DEFAULT_INT);
            defaultValues.put(Long.class.getName(), DEFAULT_LONG);
            defaultValues.put(Float.class.getName(), DEFAULT_FLOAT);
            defaultValues.put(Double.class.getName(), DEFAULT_DOUBLE);
        }

        public static Object getDefaultValue(String className) {
        	return defaultValues.get(className);
        }
    }
}
