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
 * Converter for {@link TabularData}
 *
 * @author roland
 * @since 28.09.11
 */
class TabularDataConverter extends OpenTypeConverter<TabularType> {

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
        JSONAware pValue = toJSON(pFrom);
        CompositeType rowType = pType.getRowType();
        if (rowType.containsKey("key") && rowType.containsKey("value") && rowType.keySet().size() == 2) {
            return convertToTabularTypeFromMap(pType, pValue, rowType);
        }

        // =====================================================================================
        // Its a plain TabularData, which is converted from a maps of maps

        TabularDataSupport tabularData = new TabularDataSupport(pType);
        if (!(pValue instanceof JSONObject)) {
            throw new IllegalArgumentException("Expected JSON type for a TabularData is JSONObject, not " + pValue.getClass());
        }
        putRowsToTabularData(tabularData, (JSONObject) pValue, pType.getIndexNames().size());

        return tabularData;
    }

    // =========================================================================================

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
            map.put("key", getDispatcher().convertToObject(pRowType.getType("key"), entry.getKey()));
            map.put("value", getDispatcher().convertToObject(pRowType.getType("value"), entry.getValue()));

            try {
                CompositeData compositeData = new CompositeDataSupport(pRowType, map);
                tabularData.put(compositeData);
            } catch (OpenDataException e) {
                throw new IllegalArgumentException(e.getMessage(),e);
            }
        }

        return tabularData;
    }
}
