package org.jolokia.server.core.util;/*
 *
 * Copyright 2015 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility for JSON handling
 *
 * @author roland
 * @since 13/01/16
 */
public class JsonUtil {

    /**
     * Add a Map value to another Map but dont override an existing value. If a single value already exists for this
     * key, the value is converted to a {@link JSONArray} and the old and new values are added. If the existing
     * value already is a list, the add the new value to this list.
     *
     * @param pMap the map to add to
     * @param pKey the key under which to add
     * @param pValue the map value to add to this map.
     */
    public static void addJSONObjectToJSONObject(JSONObject pMap, String pKey, JSONObject pValue) {
        Object ops = pMap.opt(pKey);
        if (ops != null) {
            if (ops instanceof JSONArray) {
                // If it is already a list, simply add it to the end
                ((JSONArray) ops).put(pValue);
            } else {
                // If it is a map, add a list with two elements
                // (the old one and the new one)
                JSONArray opList = new JSONArray();
                opList.put(ops);
                opList.put(pValue);
                pMap.put(pKey, opList);
            }
        } else {
            // No value set yet, simply add the map as plain value
            pMap.put(pKey, pValue);
        }
    }

}
