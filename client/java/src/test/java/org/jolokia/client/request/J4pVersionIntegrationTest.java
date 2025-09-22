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
package org.jolokia.client.request;

import java.util.Set;

import org.jolokia.client.exception.J4pException;
import org.jolokia.server.core.Version;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author roland
 * @since Apr 26, 2010
 */
public class J4pVersionIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void versionGetRequest() throws J4pException {
        J4pVersionRequest req = new J4pVersionRequest();
        J4pVersionResponse resp = j4pClient.execute(req);
        verifyResponse(resp);
    }

    @Test
    public void versionPostRequest() throws J4pException {
        for (J4pTargetConfig cfg : new J4pTargetConfig[]{null, getTargetProxyConfig()}) {
            J4pVersionRequest req = new J4pVersionRequest(cfg);
            req.setPreferredHttpMethod("POST");
            J4pVersionResponse resp = j4pClient.execute(req);
            verifyResponse(resp);
        }
    }

    private void verifyResponse(J4pVersionResponse pResp) {
        assertEquals("Proper agent version", Version.getAgentVersion(), pResp.getAgentVersion());
        assertEquals("Proper protocol version", Version.getProtocolVersion(), pResp.getProtocolVersion());
        assertTrue("Request timestamp", pResp.getRequestDate().getTime() <= System.currentTimeMillis());
        assertEquals("Jetty", "jetty", pResp.getProduct());
        assertTrue("Mortbay or Eclipse", pResp.getVendor().contains("Eclipse") || pResp.getVendor().contains("Mortbay"));
        Set<String> providers = pResp.getProviders();
        assertTrue(providers.contains("jmx"));
        assertEquals(0, pResp.getExtraInfo("jmx").size());
    }

}
