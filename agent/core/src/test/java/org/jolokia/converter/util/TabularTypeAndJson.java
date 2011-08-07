package org.jolokia.converter.util;

import javax.management.openmbean.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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

/**
* @author roland
* @since 07.08.11
*/
public class TabularTypeAndJson {
    TabularType type;
    JSONArray   json;

    public TabularTypeAndJson(String[] index, CompositeTypeAndJson taj, Object... rowVals) throws OpenDataException {
        CompositeType cType = taj.getType();
        json = new JSONArray();
        json.add(taj.getJson());
        int nrCols = cType.keySet().size();
        for (int i = 0 ; i < rowVals.length; i += nrCols) {
            JSONObject row = new JSONObject();
            for (int j = 0; j < nrCols; j++) {
                row.put(taj.getKey(j),rowVals[i+j]);
            }
            json.add(row);
        }
        //System.out.println(json.toJSONString());
        type = new TabularType("test","test",cType,index);
    }

    public TabularType getType() {
        return type;
    }

    public JSONArray getJson() {
        return json;
    }

    public String getJsonAsString() {
        return json.toJSONString();
    }
}
