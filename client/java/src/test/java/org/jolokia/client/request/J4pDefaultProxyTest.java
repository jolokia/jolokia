package org.jolokia.client.request;

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

import java.util.List;

import javax.management.MalformedObjectNameException;

import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.exception.*;
import org.jolokia.client.response.JolokiaReadResponse;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Integration test for reading attributes
 *
 * @author roland
 * @since Apr 27, 2010
 */
public class J4pDefaultProxyTest extends AbstractJ4pIntegrationTest {

    @Test
    public void baseTest() throws MalformedObjectNameException, J4pException {
        JolokiaReadResponse resp = j4pClient.execute(new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage"));
        assertFalse(resp.getRequest().toJson().containsKey("target"));
        assertNotNull(resp.getValue());
    }

    @Test
    public void baseBulkTest() throws MalformedObjectNameException, J4pException {
        try {
            j4pClient.execute(
                    new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage"),
                    new JolokiaReadRequest("java.lang:type=Thread","ThreadNumber"));
            fail();
        } catch (J4pBulkRemoteException exp) {
            List<JolokiaReadResponse> resps = exp.getResponses();
            assertNotNull(resps.get(0).getValue());
            J4pRemoteException jExp = exp.getRemoteExceptions().get(0);
            assertTrue(jExp.getRemoteStackTrace().contains("RMI"));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Proxy requests should be sent using POST method.*")
    public void invalidMethodTest() throws MalformedObjectNameException, J4pException {
        JolokiaReadRequest req = new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage");
        req.setPreferredHttpMethod(HttpMethod.GET);
        j4pClient.execute(req);
    }

    @Override
    protected J4pClient createJ4pClient(String url) {
        JolokiaTargetConfig config = getTargetProxyConfig();
        return new J4pClientBuilder()
                .url(url)
                .user("jolokia")
                .password("jolokia")
//                .pooledConnections()
                .target(config.url())
                .build();
    }
}
