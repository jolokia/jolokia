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

import java.nio.file.Path;
import java.util.List;

import javax.management.MalformedObjectNameException;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
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
public class ClientDefaultProxyTest extends AbstractClientIntegrationTest {

    @Test
    public void baseTest() throws MalformedObjectNameException, JolokiaException {
        JolokiaReadResponse resp = jolokiaClient.execute(new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage"));
        assertFalse(resp.getRequest().toJson().containsKey("target"));
        assertNotNull(resp.getValue());
    }

    @Test
    public void baseBulkTest() throws MalformedObjectNameException, JolokiaException {
        try {
            jolokiaClient.execute(
                    new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage"),
                    new JolokiaReadRequest("java.lang:type=Thread","ThreadNumber"));
            fail();
        } catch (JolokiaBulkRemoteException exp) {
            List<JolokiaReadResponse> resps = exp.getResponses();
            assertNotNull(resps.get(0).getValue());
            JolokiaRemoteException jExp = exp.getRemoteExceptions().get(0);
            assertTrue(jExp.getRemoteStackTrace().contains("RMI"));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Proxy requests should be sent using POST method.*")
    public void invalidMethodTest() throws MalformedObjectNameException, JolokiaException {
        JolokiaReadRequest req = new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage");
        req.setPreferredHttpMethod(HttpMethod.GET);
        jolokiaClient.execute(req);
    }

    @Override
    protected JolokiaClient createJolokiaClient(String url) {
        JolokiaTargetConfig config = getTargetProxyConfig();
        return new JolokiaClientBuilder()
                .url(url).user("jolokia").password("jolokia")
                .protocolVersion("TLSv1.3")
                .keystore(Path.of("../java/src/test/resources/certificates/client.p12"))
                .keystorePassword("1234")
                .keyPassword("1234")
                .truststore(Path.of("../java/src/test/resources/certificates/server.p12"))
                .truststorePassword("1234")
                .target(config.url())
                .build();
    }
}
