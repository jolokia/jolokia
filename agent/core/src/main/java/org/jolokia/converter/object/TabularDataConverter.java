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

import java.util.*;

import javax.management.openmbean.*;

import org.json.simple.*;

/**
 * Converter for {@link TabularData}
 *
 * @author roland
 * @since 28.09.11
 */
class TabularDataConverter extends OpenTypeConverter<TabularType> {

    // Fixed key names for tabular data represention maps for MXBeans.
    private static final String TD_KEY_KEY = "key";
    private static final String TD_KEY_VALUE = "value";

    /**
     * Constructor
     *
     * @param pDispatcher parent converter used for recursive conversion
     */
    public TabularDataConverter(OpenTypeConverter pDispatcher) {
        super(pDispatcher);
    }

    /** {@inheritDoc} */
    @Override
    boolean canConvert(OpenType pType) {
        return pType instanceof TabularType;
    }

    /** {@inheritDoc} */
    @Override
    Object convertToObject(TabularType pType, Object pFrom) {

        JSONObject value = getAsJsonObject(pFrom);

        // Convert simple map representation (with rowtype "key" and "value")
        if (checkForMapAttributeWithSimpleKey(pType)) {
            return convertToTabularTypeFromMap(pType, value);
        }

        // If it is given a a full representation (with "indexNames" and "values"), then parse this
        // accordingly
        if (checkForFullTabularDataRepresentation(value, pType)) {
            return convertTabularDataFromFullRepresentation(value, pType);
        }

        // Its a plain TabularData, which is tried to convert rom a maps of maps
        TabularDataSupport tabularData = new TabularDataSupport(pType);
        // Recursively go down the map and collect the values
        putRowsToTabularData(tabularData, value, pType.getIndexNames().size());
        return tabularData;
    }

    // =========================================================================================================

    private JSONObject getAsJsonObject(Object pFrom) {
        JSONAware jsonVal = toJSON(pFrom);
        if (!(jsonVal instanceof JSONObject)) {
            throw new IllegalArgumentException("Expected JSON type for a TabularData is JSONObject, not " + jsonVal.getClass());
        }
        return (JSONObject) jsonVal;
    }

    private boolean checkForMapAttributeWithSimpleKey(TabularType pType) {
        // The key and value must have a specific format
        return checkForMapKey(pType) && checkForMapValue(pType);
    }

    // A map is translated into a TabularData with a rowtype with two entries: "value" and "key"
    private boolean checkForMapValue(TabularType pType) {
        CompositeType rowType = pType.getRowType();
        // Two entries in the row: "key" and "value"
        return rowType.containsKey(TD_KEY_VALUE) && rowType.containsKey(TD_KEY_KEY) && rowType.keySet().size() == 2;
    }

    // The tabular data representing a map must have a single index named "key" which must be a simple type
    private boolean checkForMapKey(TabularType pType) {
        List<String> indexNames = pType.getIndexNames();
        return
                // Single index named "key"
                indexNames.size() == 1 && indexNames.contains(TD_KEY_KEY) &&
                // Only convert to map for simple types for all others use normal conversion. See #105 for details.
                pType.getRowType().getType(TD_KEY_KEY) instanceof SimpleType;
    }

    // Check for a full table data representation and do some sanity checks
    private boolean checkForFullTabularDataRepresentation(JSONObject pValue, TabularType pType) {
        if (pValue.containsKey("indexNames") && pValue.containsKey("values") && pValue.size() == 2) {
            Object jsonVal = pValue.get("indexNames");
            if (!(jsonVal instanceof JSONArray)) {
                throw new IllegalArgumentException("Index names for tabular data must given as JSON array, not " + jsonVal.getClass());
            }
            JSONArray indexNames = (JSONArray) jsonVal;
            List<String> tabularIndexNames = pType.getIndexNames();
            if (indexNames.size() != tabularIndexNames.size()) {
                throw new IllegalArgumentException("Given array with index names must have " + tabularIndexNames.size() + " entries " +
                                                   "(given: " + indexNames + ", required: " + tabularIndexNames + ")");
            }
            for (Object index : indexNames) {
                if (!tabularIndexNames.contains(index.toString())) {
                    throw new IllegalArgumentException("No index with name '" + index + "' known " +
                                                       "(given: " + indexNames + ", required: " + tabularIndexNames + ")");
                }
            }
            return true;
        }
        return false;
    }

    private TabularData convertToTabularTypeFromMap(TabularType pType, JSONObject pValue) {
        CompositeType rowType = pType.getRowType();

        // A TabularData is requested for mapping a map for the call to an MXBean
        // as described in http://download.oracle.com/javase/6/docs/api/javax/management/MXBean.html
        // This means, we will convert a JSONObject to the required format
        TabularDataSupport tabularData = new TabularDataSupport(pType);

        @SuppressWarnings("unchecked")
        Map<String, String> jsonObj = (Map<String,String>) pValue;
        for(Map.Entry<String, String> entry : jsonObj.entrySet()) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("key", getDispatcher().convertToObject(rowType.getType("key"), entry.getKey()));
            map.put("value", getDispatcher().convertToObject(rowType.getType("value"), entry.getValue()));

            try {
                CompositeData compositeData = new CompositeDataSupport(rowType, map);
                tabularData.put(compositeData);
            } catch (OpenDataException e) {
                throw new IllegalArgumentException(e.getMessage(),e);
            }
        }

        return tabularData;
    }

    // Convert complex representation containing "indexNames" and "values"
    private TabularData convertTabularDataFromFullRepresentation(JSONObject pValue, TabularType pType) {
        JSONAware jsonVal;
        jsonVal = (JSONAware) pValue.get("values");
        if (!(jsonVal instanceof JSONArray)) {
            throw new IllegalArgumentException("Values for tabular data of type " +
                                               pType + " must given as JSON array, not " + jsonVal.getClass());
        }

        TabularDataSupport tabularData = new TabularDataSupport(pType);
        for (Object val : (JSONArray) jsonVal) {
            if (!(val instanceof JSONObject)) {
                throw new IllegalArgumentException("Tabular-Data values must be given as JSON objects, not " + val.getClass());
            }
            tabularData.put((CompositeData) getDispatcher().convertToObject(pType.getRowType(), val));
        }
        return tabularData;
    }

    private void putRowsToTabularData(TabularDataSupport pTabularData, JSONObject pValue, int pLevel) {
        TabularType type = pTabularData.getTabularType();
        for (Object value : pValue.values()) {
            if (!(value instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "Cannot convert " + pValue + " to type " +
                        type + " because the object values provided (" + value.getClass() + ") is not of the expected type JSONObject at level " + pLevel);
            }
            JSONObject jsonValue = (JSONObject) value;
            if (pLevel > 1) {
                putRowsToTabularData(pTabularData, jsonValue, pLevel - 1);
            } else {
                pTabularData.put((CompositeData) getDispatcher().convertToObject(type.getRowType(), jsonValue));
            }
        }
    }
}
