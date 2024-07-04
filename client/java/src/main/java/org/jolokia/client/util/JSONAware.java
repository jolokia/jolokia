/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.client.util;

import java.io.Reader;
import java.io.Writer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * <p>Class that represents a union of:<ul>
 *     <li>{@link JSONObject}</li>
 *     <li>{@link JSONArray}</li>
 * </ul>
 * This is required, because such class was present in com.googlecode.json-simple:json-simple, but is not
 * available in org.json:json.</p>
 *
 * <p>This is a copy of similar class from jolokia-server-core. It's needed, because after moving
 * to org.json we've lost this common class.</p>
 */
public class JSONAware {

    JSONArray array;
    JSONObject object;

    public static JSONAware parse(Reader reader) {
        JSONTokener tokener = new JSONTokener(reader);
        char c = tokener.nextClean();
        JSONAware result = new JSONAware();
        tokener.back();
        if (c == '{') {
            result.object = new JSONObject(tokener);
        } else if (c == '[') {
            result.array = new JSONArray(tokener);
        } else {
            throw tokener.syntaxError("Can parse only JSON objects or arrays");
        }
        return result;
    }

    public static <T> T parse(Reader reader, Class<T> expectedClass) {
        JSONTokener tokener = new JSONTokener(reader);
        char c = tokener.nextClean();
        tokener.back();
        if (c == '{' && expectedClass == JSONObject.class) {
            return expectedClass.cast(new JSONObject(tokener));
        } else if (c == '[' && expectedClass == JSONArray.class) {
            return expectedClass.cast(new JSONArray(tokener));
        } else {
            throw tokener.syntaxError("Expected " + expectedClass + " but got JSON data starting with \"" + c + "\"");
        }
    }

    public static JSONAware with(JSONObject object) {
        JSONAware result = new JSONAware();
        result.object = object;
        return result;
    }

    public static JSONAware with(JSONArray array) {
        JSONAware result = new JSONAware();
        result.array = array;
        return result;
    }

    public void writeJSONString(Writer writer) {
        if (object != null) {
            object.write(writer);
        } else {
            array.write(writer);
        }
    }

    public boolean isArray() {
        return array != null;
    }

    public JSONArray getArray() {
        return array;
    }

    public boolean isObject() {
        return object != null;
    }

    public JSONObject getObject() {
        return object;
    }

    public String toJSONString() {
        if (object != null) {
            return object.toString();
        } else {
            return array.toString();
        }
    }

}
