package org.jolokia.converter.object;

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

import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.*;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

/**
 * Converter for {@link CompositeType} objects
 *
 * @author roland
 * @since 28.09.11
 */
class CompositeTypeConverter extends OpenTypeConverter<CompositeType> {

    /**
     * Constructor
     * @param pDispatcher parent dispatcher for converting recursively
     */
    CompositeTypeConverter(OpenTypeConverter pDispatcher) {
        super(pDispatcher);
    }

    /** {@inheritDoc} */
    @Override
    boolean canConvert(OpenType pType) {
        return pType instanceof CompositeType;
    }

    /** {@inheritDoc} */
    @Override
    Object convertToObject(CompositeType pType, Object pFrom) {
        // break down the composite type to its field and recurse for converting each field
        JSONAware value = toJSON(pFrom);
        if (!(value instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "Conversion of " + value + " to " +
                    pType + " failed because provided JSON type " + value.getClass() + " is not a JSONObject");
        }

        Map<String, Object> givenValues = (JSONObject) value;
        Map<String, Object> compositeValues = new HashMap<String, Object>();

        fillCompositeWithGivenValues(pType, compositeValues, givenValues);
        completeCompositeValuesWithDefaults(pType, compositeValues);

        try {
            return new CompositeDataSupport(pType, compositeValues);
        } catch (OpenDataException e) {
            throw new IllegalArgumentException("Internal error: " + e.getMessage(),e);
        }
    }

    // ========================================================================================

    private void fillCompositeWithGivenValues(CompositeType pType, Map<String, Object> pCompositeValues, Map<String, Object> pSourceJson) {
        for (Map.Entry<String,Object> entry : pSourceJson.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!pType.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Conversion to CompositeType failed because " + key + " is not known as composite attribute key.");
            }
            if (value != null) {
                Object convertedValue = getDispatcher().convertToObject(pType.getType(key),value);
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

    // List of default values to use
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
