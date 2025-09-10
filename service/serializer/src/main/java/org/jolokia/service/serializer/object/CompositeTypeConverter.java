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
package org.jolokia.service.serializer.object;

import java.util.HashMap;
import java.util.Map;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

/**
 * Converter for {@link CompositeType} objects. This converter transforms maps (in particular
 * {@link org.jolokia.json.JSONObject JSON objects} into {@link javax.management.openmbean.CompositeData} values
 * according to the type specification defined in a {@link CompositeType}.
 *
 * @author roland
 * @since 28.09.11
 */
class CompositeTypeConverter extends OpenTypeConverter<CompositeType> {

    /**
     * Constructor
     *
     * @param pOpenTypeDeserializer parent converter used for recursive conversion
     */
    CompositeTypeConverter(OpenTypeDeserializer pOpenTypeDeserializer) {
        super(pOpenTypeDeserializer);
    }

    @Override
    boolean canConvert(OpenType<?> pType) {
        return pType instanceof CompositeType;
    }

    @Override
    public Object convert(CompositeType pType, Object pValue) {
        Map<String, Object> map = toMap(pValue);

        // map to collect all values from the passed map - we need them for CompositeDataSupport constructor
        Map<String, Object> compositeValues = new HashMap<>();

        fillCompositeWithGivenValues(pType, compositeValues, map);
        completeCompositeValuesWithDefaults(pType, compositeValues);

        try {
            return new CompositeDataSupport(pType, compositeValues);
        } catch (OpenDataException e) {
            throw new IllegalArgumentException("Can't convert " + pValue.getClass().getName()
                + " to javax.management.openmbean.CompositeType: " + e.getMessage(), e);
        }
    }

    // ========================================================================================

    /**
     * Collect values according to {@link CompositeType} with validation. Collected values will be passed to
     * {@link CompositeDataSupport} constructor.
     *
     * @param pType
     * @param pCompositeValues
     * @param pMap
     */
    private void fillCompositeWithGivenValues(CompositeType pType, Map<String, Object> pCompositeValues, Map<String, Object> pMap) {
        for (Map.Entry<String, Object> entry : pMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!pType.containsKey(key)) {
                // Some Oracle JVM return Objects with more fields than the overridden/official type (Example ThreadInfo)
                // in that case skip additional fields
                if (isForgiving()) {
                    continue;
                } else {
                    throw new IllegalArgumentException("Conversion to CompositeData failed because \""
                        + key + "\" is not a valid key of the target CompositeType.");
                }
            }
            // don't put null values - will be added (if type allows) later
            if (value != null) {
                Object convertedValue = openTypeDeserializer.convert(pType.getType(key), value);
                pCompositeValues.put(key, convertedValue);
            }
        }
    }

    /**
     * Add values for all keys declared in {@link CompositeType} which are missing - we'll collect defaults for
     * these keys
     *
     * @param pType
     * @param pCompositeValues
     */
    private void completeCompositeValuesWithDefaults(CompositeType pType, Map<String, Object> pCompositeValues) {
        // fields that were not given in the JSON must be added with
        // null for Objects and the default value for primitives
        for (String itemName : pType.keySet()) {
            if (!pCompositeValues.containsKey(itemName)) {
                Object itemValue = null;
                OpenType<?> itemType = pType.getType(itemName);
                if (itemType instanceof SimpleType) {
                    SimpleType<?> sType = (SimpleType<?>) itemType;
                    itemValue = DEFAULT_PRIMITIVE_VALUES.get(sType.getClassName());
                }
                pCompositeValues.put(itemName, itemValue);
            }
        }
    }

    // List of default values to use
    private static final Map<String, Object> DEFAULT_PRIMITIVE_VALUES = new HashMap<>();

    static {
        DEFAULT_PRIMITIVE_VALUES.put(Boolean.class.getName(), false);
        DEFAULT_PRIMITIVE_VALUES.put(Character.class.getName(), '\u0000');
        DEFAULT_PRIMITIVE_VALUES.put(Byte.class.getName(), 0);
        DEFAULT_PRIMITIVE_VALUES.put(Short.class.getName(), 0);
        DEFAULT_PRIMITIVE_VALUES.put(Integer.class.getName(), 0);
        DEFAULT_PRIMITIVE_VALUES.put(Long.class.getName(), 0L);
        DEFAULT_PRIMITIVE_VALUES.put(Float.class.getName(), 0.0f);
        DEFAULT_PRIMITIVE_VALUES.put(Double.class.getName(), 0.0d);
    }

}
