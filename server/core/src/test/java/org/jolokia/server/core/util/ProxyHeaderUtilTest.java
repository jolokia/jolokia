/*
 * Copyright 2009-2026 Roland Huss
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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ProxyHeaderUtilTest {

    @Test
    public void minimal() {
        String[] addresses = ProxyHeaderUtil.addressChain(false, "127.0.0.1", null, null, null);
        assertEquals(addresses.length, 1);
        assertEquals(addresses[0], "127.0.0.1");
    }

    @Test
    public void standardForwarded() {
        // good, long list (by=xxx should be skipped - I check only for=xxx entries)
        String forwarded = "for=\"124.2.3.1\",by=10.0.0.1,for=223.2.1.3,for=223.2.1.3,for=\"[3:2::]\",for=1.2.3.4:5678,for = \"[1::2]:333\"";
        String[] addresses = ProxyHeaderUtil.addressChain(false, "127.0.0.1", null, null, forwarded);
        assertEquals(addresses.length, 6);
        assertEquals(addresses[0], "124.2.3.1");
        assertEquals(addresses[1], "223.2.1.3");
        assertEquals(addresses[2], "3:2::");
        assertEquals(addresses[3], "1.2.3.4");
        assertEquals(addresses[4], "1::2");
        assertEquals(addresses[5], "127.0.0.1");

        // nasty list
        forwarded = "for=\"124.2.3.1,,,for=223..3";
        addresses = ProxyHeaderUtil.addressChain(false, "127.0.0.1", null, null, forwarded);
        assertEquals(addresses.length, 3);
        assertEquals(addresses[0], "\"124.2.3.1"); // it should be rejected by the restrictor
        assertEquals(addresses[1], "223..3");      // it should be rejected by restrictor
        assertEquals(addresses[2], "127.0.0.1");
    }

    @Test
    public void xForwarded() {
        // good, long list
        String xforwarded = "\"124.2.3.1\",1.2.3.4:5678,223.2.1.3,\"[3:2::]\",1.2.3.4:5678,\"[1::2]:333\"";
        String[] addresses = ProxyHeaderUtil.addressChain(false, "127.0.0.1", null, xforwarded, null);
        assertEquals(addresses.length, 6);
        assertEquals(addresses[0], "124.2.3.1");
        assertEquals(addresses[1], "1.2.3.4");
        assertEquals(addresses[2], "223.2.1.3");
        assertEquals(addresses[3], "3:2::");
        assertEquals(addresses[4], "1::2");
        assertEquals(addresses[5], "127.0.0.1");

        // nasty list
        xforwarded = "\"124.2.3.1,,,223..3";
        addresses = ProxyHeaderUtil.addressChain(false, "127.0.0.1", null, xforwarded, null);
        assertEquals(addresses.length, 3);
        assertEquals(addresses[0], "\"124.2.3.1"); // it should be rejected by the restrictor
        assertEquals(addresses[1], "223..3");      // it should be rejected by restrictor
        assertEquals(addresses[2], "127.0.0.1");
    }

    @Test
    public void xRealIp() {
        String[] addresses = ProxyHeaderUtil.addressChain(false, "127.0.0.1", "1.2.3.4", null, null);
        assertEquals(addresses.length, 2);
        assertEquals(addresses[0], "1.2.3.4");
        assertEquals(addresses[1], "127.0.0.1");
    }

    @Test
    public void trustedStandardForwarded() {
        // good, long list
        String forwarded = "for=\"124.2.3.1\",for=223.2.1.3,for=\"[3:2::]\",for=1.2.3.4:5678,for = \"[1::2]:333\"";
        String[] addresses = ProxyHeaderUtil.addressChain(true, "127.0.0.1", null, null, forwarded);
        assertEquals(addresses.length, 2);
        assertEquals(addresses[0], "124.2.3.1");
        assertEquals(addresses[1], "127.0.0.1");

        // nasty list
        forwarded = "for=\"124.2.3.1,,,for=223..3";
        addresses = ProxyHeaderUtil.addressChain(true, "127.0.0.1", null, null, forwarded);
        assertEquals(addresses.length, 2);
        assertEquals(addresses[0], "\"124.2.3.1"); // it should be rejected by the restrictor
        assertEquals(addresses[1], "127.0.0.1");
    }

    @Test
    public void trustedXForwarded() {
        // good, long list
        String xforwarded = "\"124.2.3.1\",223.2.1.3,\"[3:2::]\",1.2.3.4:5678,\"[1::2]:333\"";
        String[] addresses = ProxyHeaderUtil.addressChain(true, "127.0.0.1", null, xforwarded, null);
        assertEquals(addresses.length, 2);
        assertEquals(addresses[0], "124.2.3.1");
        assertEquals(addresses[1], "127.0.0.1");

        // nasty list
        xforwarded = "\"124.2.3.1,,,223..3";
        addresses = ProxyHeaderUtil.addressChain(true, "127.0.0.1", null, xforwarded, null);
        assertEquals(addresses.length, 2);
        assertEquals(addresses[0], "\"124.2.3.1"); // it should be rejected by the restrictor
        assertEquals(addresses[1], "127.0.0.1");
    }

    @Test
    public void trustedXRealIp() {
        String[] addresses = ProxyHeaderUtil.addressChain(true, "127.0.0.1", "1.2.3.4", null, null);
        assertEquals(addresses.length, 2);
        assertEquals(addresses[0], "1.2.3.4");
        assertEquals(addresses[1], "127.0.0.1");
    }

}
