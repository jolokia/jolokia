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
package org.jolokia.converter.util;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import org.jolokia.json.JSONObject;

/**
 * <p>{@link TabularData} is not simply an array. Arrays (and {@link ArrayType} values) are indexed with int.
 * {@link TabularData} values are indexed by declared number of index keys (of String type) and the values
 * are {@link CompositeData} of specified (single) {@link CompositeType}.</p>
 *
 * <p>The array of String indexes need to be a subset of keys that can be passed to
 * {@link CompositeType#getType(String)}.</p>
 *
 * @author roland
 * @since 07.08.11
*/
public class TabularTypeAndJson {
    TabularType type;
    JSONObject json;
    CompositeTypeAndJson caj;

    /**
     * Create a {@link TabularType} (not {@link TabularData}) and its Jolokia JSON representation as {@link JSONObject}.
     *
     * @param index   array of String keys as subset of keys from {@link CompositeType}
     * @param caj     {@link CompositeType} (not value) needed to construct {@link TabularType} inside
     *                {@link CompositeTypeAndJson} which already contains "first row" of values for the data
     *                of this {@link TabularType}
     * @param rowVals 1D array of flattened rows processed in groups according to indexes of the
     *                {@link TabularType#getRowType()} - these are added after the data found in
     *                {@link CompositeTypeAndJson}
     * @throws OpenDataException
     */
    public TabularTypeAndJson(String[] index, CompositeTypeAndJson caj, Object... rowVals) throws OpenDataException {
        this.caj = caj;
        CompositeType cType = caj.getType();
        json = new JSONObject();
        int nrCols = cType.keySet().size();

        // first row from the composite data
        addRow(json, caj.getJson(), index);

        // more rows from the passed values
        for (int i = 0; i < rowVals.length; i += nrCols) {
            JSONObject row = new JSONObject();
            for (int j = 0; j < nrCols; j++) {
                row.put(caj.getKey(j), rowVals[i + j]);
            }
            addRow(json, row, index);
        }

        // construct the TabularType from CompositeType and selected indexes
        type = new TabularType("test", "test", cType, index);
    }

    public TabularType getType() {
        return type;
    }

    public JSONObject getJson() {
        return json;
    }

    public String getJsonAsString() {
        return json.toJSONString();
    }

    /**
     * Construct a row for {@link TabularData}. For multi-key indexes, the ultimate value is stored
     * under multiply nested keys
     *
     * @param pJson
     * @param pRow
     * @param pIndex
     */
    private void addRow(JSONObject pJson, JSONObject pRow, String[] pIndex) {
        JSONObject map = pJson;
        for (int i = 0; i < pIndex.length - 1; i++) {
            String key = (String) pRow.get(pIndex[i]);
            if (key == null) {
                return;
            }
            JSONObject inner = (JSONObject) map.get(key);
            if (inner == null) {
                inner = new JSONObject();
                map.put(key, inner);
            }
            map = inner;
        }
        Object key = pRow.get(pIndex[pIndex.length - 1]);
        map.put(key == null ? null : key.toString(), pRow);
    }

}
