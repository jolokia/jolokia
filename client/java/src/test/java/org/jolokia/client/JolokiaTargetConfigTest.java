package org.jolokia.client;

/*
 * Copyright 2009-2013 Roland Huss
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

import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 27.12.11
 */
public class JolokiaTargetConfigTest {

    static String URL = "http://localhost:8080";

    @Test
    public void simple() {
        JolokiaTargetConfig cfg = new JolokiaTargetConfig(URL,"roland","s!cr!t");
        assertEquals(cfg.url(),URL);
        assertEquals(cfg.user(),"roland");
        assertEquals(cfg.password(),"s!cr!t");
    }

    @Test
    public void json() {
        JolokiaTargetConfig cfg = new JolokiaTargetConfig(URL,"roland","s!cr!t");
        JSONObject j = cfg.toJson();
        assertEquals(j.get("url"),URL);
        assertEquals(j.get("user"),"roland");
        assertEquals(j.get("password"),"s!cr!t");
    }
}
