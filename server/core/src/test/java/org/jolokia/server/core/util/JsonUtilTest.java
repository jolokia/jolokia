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
package org.jolokia.server.core.util;

import java.io.StringReader;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class JsonUtilTest {

    @Test
    public void parseStringOrArray() {
        JSONTokener tokener = new JSONTokener(new StringReader("{\"a\":\"b\"}"));
        assertEquals(tokener.nextClean(), '{');
        tokener.back();
        assertEquals(new JSONObject(tokener).toMap().get("a"), "b");
    }

}
