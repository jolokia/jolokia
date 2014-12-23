package org.jolokia.client;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.net.URISyntaxException;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23/12/14
 */
public class ProxyConfigTest {


    @Test
    public void testInvalidArguments() throws Exception {
        for (String spec : new String[] {
                "",
                "host",
                "host port",
                "host:port",
                "host:8080"
        }) {
            try {
                new J4pClientBuilder.Proxy(spec);
                fail();
            } catch (URISyntaxException exp) {
                // that's expected
            }
            J4pClientBuilder.Proxy proxy = J4pClientBuilder.parseProxySettings(spec);
            assertNull(proxy);
        }
    }


    @Test
    public void testParseProxySettings_schemaHostColonPost() throws Exception {
        String testData[][] = {
                { "http://host:8080", "host", "8080", null, null },
                { "http://user@host:8080", "host", "8080", "user", null },
                { "http://user:pass@host:8080", "host", "8080", "user", "pass" },
        };
        for (String[] d : testData) {
            J4pClientBuilder.Proxy proxy = new J4pClientBuilder.Proxy(d[0]);
            assertNotNull(proxy);
            assertEquals(d[1], proxy.getHost());
            assertEquals(Integer.parseInt(d[2]),proxy.getPort());
            assertEquals(d[3],proxy.getUser());
            assertEquals(d[4],proxy.getPass());
        }
    }

}
