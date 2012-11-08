package org.jolokia.request;

/*
 *  Copyright 2009-2010 Roland Huss
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

import org.jolokia.backend.BackendManager;
import org.jolokia.util.ConfigKey;
import org.jolokia.util.LogHandler;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


/**
 */
public class RawObjectNameTest {
    private Map config = new HashMap();
    private LogHandler logHandler = new LogHandler() {
        public void debug(String message) {
            System.out.println("[DEBUG] " + message);
        }

        public void info(String message) {
            System.out.println("[INFO] " + message);
        }

        public void error(String message, Throwable t) {
            System.out.println("[ERROR] " + message);
            t.printStackTrace();
        }
    };
    private BackendManager backendManager = new BackendManager(config, logHandler, null, true /* Lazy Init */ );

    @Test
    public void testRawObjectNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(backendManager, false);
    }

    @Test
    public void testCanonicalNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(backendManager, true);
    }

    @AfterClass
    public void destroy() throws Exception {
        backendManager.destroy();
    }

    private void assertPropertyNamesOrderedCorrectly(BackendManager backendManager, boolean canonical) throws Exception {
        JmxRequestBuilder builder = new JmxRequestBuilder(RequestType.LIST).path("java.lang");
        if (!canonical) {
            builder = builder.option(ConfigKey.MBEAN_CANONICAL_PROPERTIES, "false");
        }
        JmxListRequest req = builder.build();
        JSONObject json = backendManager.handleRequest(req);
        JSONObject value = (JSONObject) json.get("value");
        Set keys = value.keySet();
        String memoryKey = null;
        for (Object key : keys) {
            String keyText = key.toString();
            if (keyText.contains("MemoryPool")) {
                memoryKey = keyText;
            }
        }
        assertNotNull("Should have found a JMX key matching java.lang:type=MemoryPool", memoryKey);

        // canonical order will have name first; raw format should start with type=MemoryPool
        String prefix = "type=MemoryPool";
        if (canonical) {
            assertFalse(memoryKey.startsWith(prefix), "Raw order should not start with '" + prefix + "' but was '"
                    + memoryKey + "' which is probably the raw unsorted order?");
        } else {
            assertTrue(memoryKey.startsWith(prefix), "Raw order should start with '" + prefix + "' but was '"
                    + memoryKey + "' which is probably the canonical sorted order?");
        }
    }

}
