package org.jolokia.converter.object;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.*;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
    @SuppressWarnings("unchecked")
	public Object convertToObject(OpenType openType, Object pValue) {
        if (pValue == null) {
            return null;
        } else if (openType instanceof SimpleType) {
            // SimpleTypes are converted as usual objects
            return stringToObjectConverter.prepareValue(openType.getClassName(), pValue);
		} else if (openType instanceof ArrayType<?>) {
            return convertArrayType((ArrayType) openType, toJSON(pValue));
        } else if (openType instanceof CompositeType) {
            return convertCompositeType((CompositeType) openType, toJSON(pValue));
        } else if (openType instanceof TabularType) {
            return convertToTabularType((TabularType) openType, toJSON(pValue));
		} else {
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to " + openType + ": " + "No converter could be found");
        }
	}

    // =======================================================================================================

    private Object[] convertArrayType(ArrayType pType, JSONAware pValue) {
        // prepare each value in the array and then process the array of values
        if (!(pValue instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to type " +
                    pType + " because JSON object type " + pValue.getClass() + " is not a JSONArray");

        }

        JSONArray jsonArray = (JSONArray) pValue;
        OpenType elementOpenType = pType.getElementOpenType();
        Object[] valueArray = createTargetArray(elementOpenType, jsonArray.size());

        int i = 0;
        for (Object element : jsonArray) {
            valueArray[i++] = convertToObject(elementOpenType, element);
        }

        return valueArray;
    }

    // -----

    private CompositeData convertCompositeType(CompositeType pType, JSONAware pValue) {
        // break down the composite type to its field and recurse for converting each field
        if (!(pValue instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to " +
                    pType + " because provided JSON type " + pValue.getClass() + " is not a JSONObject");
        }

        Map<String, Object> givenValues = (JSONObject) pValue;
        Map<String, Object> compositeValues = new HashMap<String, Object>();

        fillCompositeWithGivenValues(pType, compositeValues, givenValues);
        completeCompositeValuesWithDefaults(pType, compositeValues);

        try {
            return new CompositeDataSupport(pType, compositeValues);
        } catch (OpenDataException e) {
            throw new IllegalArgumentException("Internal error: " + e.getMessage(),e);
        }
    }

    private void fillCompositeWithGivenValues(CompositeType pType, Map<String, Object> pCompositeValues, Map<String, Object> pSourceJson) {
        for (Map.Entry<String,Object> entry : pSourceJson.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!pType.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Cannot convert to CompositeType because " + key + " is not known as composite attribute key.");
            }
            if (value != null) {
                Object convertedValue = convertToObject(pType.getType(key),value);
                pCompositeValues.put(key, convertedValue);
            }
        }
    }

    private void completeCompositeValuesWithDefaults(CompositeType pType, Map<String, Object> pCompositeValues) {
        /* fields that were not given in the JSON must be added with
         * null for Objects and the default value for primitives
         */
        for (String itemName : pType.keySet()) {
            if (!pCompositeValues.containsKey(itemName)) {
                Object itemValue = null;
                OpenType itemType = pType.getType(itemName);
                if (itemType instanceof SimpleType) {
                    SimpleType sType = (SimpleType) itemType;
                    itemValue = DEFAULT_PRIMITIVE_VALUES.get(sType.getClassName());
                }
                pCompositeValues.put(itemName, itemValue);
            }
        }
    }

    // -----

    private TabularData convertToTabularType(TabularType pType, JSONAware pValue) {
        CompositeType rowType = pType.getRowType();
        if (rowType.containsKey("key") && rowType.containsKey("value") && rowType.keySet().size() == 2) {
            return convertToTabularTypeFromMap(pType, pValue, rowType);
        }

        // =====================================================================================
        // Its a plain TabularData, which is converted from an array of maps

        if (!(pValue instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to type " +
                    pType + " because the data provided (" + pValue.getClass() + ") is not a JSONArray");
        }
        TabularDataSupport tabularData = new TabularDataSupport(pType);
        JSONArray givenValues = (JSONArray) pValue;

        for (Object element : givenValues) {
            if (!(element instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "Illegal structure for TabularData: Must be an array of maps, not an array of " + element.getClass());
            }
            tabularData.put(convertCompositeType(rowType, (JSONObject) element));
        }
        return tabularData;
    }

    private TabularData convertToTabularTypeFromMap(TabularType pType, JSONAware pValue, CompositeType pRowType) {
        // A TabularData is requested for mapping a map for the call to an MXBean
        // as described in http://download.oracle.com/javase/6/docs/api/javax/management/MXBean.html
        // This means, we will convert a JSONObject to the required format
        TabularDataSupport tabularData = new TabularDataSupport(pType);
        if (!(pValue instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to a TabularData type for an MXBean's map representation. " +
                    "This must be a JSONObject / Map" );

        }
        @SuppressWarnings("unchecked")
        Map<String, String> jsonObj = (Map<String,String>) pValue;
        for(Map.Entry<String, String> entry : jsonObj.entrySet()) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("key", convertToObject(pRowType.getType("key"),entry.getKey()));
            map.put("value", convertToObject(pRowType.getType("value"), entry.getValue()));

            try {
                CompositeData compositeData = new CompositeDataSupport(pRowType, map);
                tabularData.put(compositeData);
            } catch (OpenDataException e) {
                throw new IllegalArgumentException(e.getMessage(),e);
            }
        }

        return tabularData;
    }

    // ------- 

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

    private JSONAware toJSON(Object pValue) {
        Class givenClass = pValue.getClass();
        if (JSONAware.class.isAssignableFrom(givenClass)) {
            return (JSONAware) pValue;
        } else {
            try {
                return (JSONAware) new JSONParser().parse(pValue.toString());
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e,e);
            } catch (ClassCastException exp) {
                throw new IllegalArgumentException("Given value " + pValue.toString() +
                                                   " cannot be parsed to JSONAware object: " + exp,exp);
            }
        }
    }

    private static final Map<String, Object> DEFAULT_PRIMITIVE_VALUES = new HashMap<String, Object>();;

    static {
        DEFAULT_PRIMITIVE_VALUES.put(Boolean.class.getName(), false);
        DEFAULT_PRIMITIVE_VALUES.put(Byte.class.getName(), 0);
        DEFAULT_PRIMITIVE_VALUES.put(Character.class.getName(),'\u0000');
        DEFAULT_PRIMITIVE_VALUES.put(Short.class.getName(), 0);
        DEFAULT_PRIMITIVE_VALUES.put(Integer.class.getName(), 0);
        DEFAULT_PRIMITIVE_VALUES.put(Long.class.getName(), 0L);
        DEFAULT_PRIMITIVE_VALUES.put(Float.class.getName(), 0.0f);
        DEFAULT_PRIMITIVE_VALUES.put(Double.class.getName(), 0.0d);
    }

}
