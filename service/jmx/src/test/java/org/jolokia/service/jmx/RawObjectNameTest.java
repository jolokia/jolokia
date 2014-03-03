package org.jolokia.service.jmx;

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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;

import org.jolokia.server.core.backend.BackendManager;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.service.serializer.JolokiaSerializer;
import org.json.simple.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 */
public class RawObjectNameTest {

    private BackendManager backendManager;


    @BeforeMethod
    public void setUp() throws Exception {

        LocalRequestHandler requestHandler = new LocalRequestHandler(0);
        TestJolokiaContext ctx = new TestJolokiaContext.Builder()
                .services(RequestHandler.class,requestHandler)
                .services(Serializer.class,new JolokiaSerializer())
                .build();
        ctx.init();
        backendManager = new BackendManager(ctx);
    }

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

    private JolokiaRequestBuilder listRequestBuilder() throws MalformedObjectNameException {
        return new JolokiaRequestBuilder(RequestType.LIST).path("java.lang");
    }

    private JolokiaRequestBuilder readRequestBuilder() throws MalformedObjectNameException {
        return new JolokiaRequestBuilder(RequestType.READ,"java.lang:*").option(ConfigKey.IGNORE_ERRORS,"true");
    }

    private JolokiaRequestBuilder searchRequestBuilder() throws MalformedObjectNameException {
        return new JolokiaRequestBuilder(RequestType.SEARCH,"java.lang:*");
    }

    private void assertPropertyNamesOrderedCorrectly(JolokiaRequestBuilder builder, boolean canonical) throws Exception {
        if (!canonical) {
            builder = builder.option(ConfigKey.CANONICAL_NAMING, "false");
        }
        JolokiaRequest req = builder.build();
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
        } else {
            assertTrue(matcher.find(), "Raw order should start with '" + prefix + "' but was '"
                    + memoryKey + "' which is probably the canonical sorted order?");
        }
    }

}
