package org.jolokia.converter.util;

import javax.management.openmbean.*;

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
public class CompositeTypeAndJson {
    JSONObject    json;
    CompositeType type;
    String keys[];


    public CompositeTypeAndJson(Object... elements) throws OpenDataException {
        OpenType types[] = new OpenType[elements.length / 3];
        keys = new String[elements.length / 3];
        Object values[] = new Object[elements.length / 3];
        int j = 0;
        for (int i = 0; i < elements.length; i+=3) {
            types[j] = (OpenType) elements[i];
            keys[j] = (String) elements[i+1];
            values[j] = elements[i+2];
            j++;
        }
        type = new CompositeType(
                "testType",
                "Type for testing",
                keys,
                keys,
                types
                );
        json = new JSONObject();
        for (int i=0; i<keys.length;i++) {
            if (values[i] != null) {
                json.put(keys[i],values[i]);
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

    public CompositeData getCompositeData() throws OpenDataException {
        return new CompositeDataSupport(type,json);
    }
}
