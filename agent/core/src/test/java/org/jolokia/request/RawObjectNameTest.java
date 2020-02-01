package org.jolokia.request;

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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;

import org.jolokia.backend.BackendManager;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.util.*;
import org.json.simple.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 */
public class RawObjectNameTest {
    private Configuration config     = new Configuration(ConfigKey.AGENT_ID, UUID.randomUUID().toString());
    private LogHandler    logHandler = new LogHandler.StdoutLogHandler(false);

    private BackendManager backendManager = new BackendManager(config, logHandler, null, true /* Lazy Init */);

    @Test
    public void testListRawObjectNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(listRequestBuilder(), false);
    }

    @Test
    public void testListCanonicalNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(listRequestBuilder(), true);
    }

    @Test
    public void testReadRawObjectNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(readRequestBuilder(), false);
    }

    @Test
    public void testReadCanonicalNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(readRequestBuilder(), true);
    }

    @Test
    public void testSearchRawObjectNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(searchRequestBuilder(), false);
    }

    @Test
    public void testSearchCanonicalNameAccess() throws Exception {
        assertPropertyNamesOrderedCorrectly(searchRequestBuilder(), true);
    }


    @AfterClass
    public void destroy() throws Exception {
        backendManager.destroy();
    }

    private JmxRequestBuilder listRequestBuilder() throws MalformedObjectNameException {
        return new JmxRequestBuilder(RequestType.LIST).path("java.lang");
    }

    private JmxRequestBuilder readRequestBuilder() throws MalformedObjectNameException {
        return new JmxRequestBuilder(RequestType.READ,"java.lang:*").option(ConfigKey.IGNORE_ERRORS,"true");
    }

    private JmxRequestBuilder searchRequestBuilder() throws MalformedObjectNameException {
        return new JmxRequestBuilder(RequestType.SEARCH,"java.lang:*");
    }

    private void assertPropertyNamesOrderedCorrectly(JmxRequestBuilder builder, boolean canonical) throws Exception {
        if (!canonical) {
            builder = builder.option(ConfigKey.CANONICAL_NAMING, "false");
        }
        JmxRequest req = builder.build();
        JSONObject json = backendManager.handleRequest(req);
        JSONAware value = (JSONAware) json.get("value");
        String memoryKey = null;
        Set keys = value instanceof JSONObject ?
                ((JSONObject) value).keySet() :
                new HashSet((JSONArray) value);
        for (Object key : keys) {
            String keyText = key.toString();
            if (keyText.contains("MemoryPool")) {
                memoryKey = keyText;
            }
        }

        assertNotNull("Should have found a JMX key matching java.lang:type=MemoryPool", memoryKey);

        // canonical order will have name first; raw format should start with type=MemoryPool
        String prefix = "^(java\\.lang:)?type=MemoryPool";
        Pattern prefixPattern = Pattern.compile(prefix);
        Matcher matcher = prefixPattern.matcher(memoryKey);
        if (canonical) {
            assertFalse(matcher.find(), "Raw order should not start with '" + prefix + "' but was '"
                    + memoryKey + "' which is probably the raw unsorted order?");
        }
    }

}
