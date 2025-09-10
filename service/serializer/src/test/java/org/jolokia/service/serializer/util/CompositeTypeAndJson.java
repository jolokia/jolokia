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
package org.jolokia.service.serializer.util;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;

import org.jolokia.json.JSONObject;

/**
 * {@link CompositeData} is a map (hashtable, dictionary) of key-value pairs, where keys are Strings. Its
 * type is described by {@link CompositeType}
 *
 * @author roland
 * @since 07.08.11
 */
public class CompositeTypeAndJson {
    JSONObject json;
    CompositeType type;
    String[] keys;

    /**
     * Create a {@link CompositeType} (not the {@link CompositeData}) and its Jolokia JSON representation as
     * {@link JSONObject}.
     *
     * @param elements
     * @throws OpenDataException
     */
    public CompositeTypeAndJson(Object... elements) throws OpenDataException {
        // for CompositeType
        keys = new String[elements.length / 3];
        OpenType<?>[] types = new OpenType[elements.length / 3];

        // for JSONObject
        Object[] values = new Object[elements.length / 3];

        int j = 0;
        for (int i = 0; i < elements.length; i += 3) {
            // for CompositeType
            keys[j] = (String) elements[i + 1];
            types[j] = (OpenType<?>) elements[i];
            // for JSONObject
            values[j] = elements[i + 2];
            j++;
        }

        // keys (items), descriptions and types are arrays of the same size
        type = new CompositeType("testType", "Type for testing", keys, keys, types);

        // Jolokia JSON representation according to CompositeType
        json = new JSONObject();
        for (int i = 0; i < keys.length; i++) {
            if (values[i] != null) {
                json.put(keys[i], values[i]);
            }
        }
    }

    public String getKey(int idx) {
        return keys[idx];
    }

    public JSONObject getJson() {
        return json;
    }

    public CompositeType getType() {
        return type;
    }

    public String getJsonAsString() {
        return json.toJSONString();
    }

    /**
     * Combine {@link CompositeType} and Jolokia JSON representation of the map and return
     * {@link CompositeData} JMX value. Used when testing extractors ({@link OpenType} value to JSON).
     *
     * @return
     * @throws OpenDataException
     */
    public CompositeData getCompositeData() throws OpenDataException {
        return new CompositeDataSupport(type, json);
    }

}
