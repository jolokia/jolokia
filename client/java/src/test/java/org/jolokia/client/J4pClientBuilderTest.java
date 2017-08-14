package org.jolokia.client;

/*
 * Copyright 2009-2011 Roland Huss
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

import javax.management.MalformedObjectNameException;

import org.apache.http.impl.client.BasicCookieStore;
import org.jolokia.client.exception.J4pConnectException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.J4pReadRequest;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * @author roland
 * @since 23.09.11
 */
public class J4pClientBuilderTest {

    @Test
    public void simple() {
        J4pClient client =
                J4pClient
                        .url("http://localhost:8080/jolokia")
                        .user("roland")
                        .password("s!c!r!t")
                        .connectionTimeout(100)
                        .expectContinue(false)
                        .tcpNoDelay(true)
                        .contentCharset("utf-8")
                        .maxConnectionPoolTimeout(3000)
                        .maxTotalConnections(500)
                        .defaultMaxConnectionsPerRoute(500)
                        .pooledConnections()
                        .socketBufferSize(8192)
                        .socketTimeout(5000)
                        .cookieStore(new BasicCookieStore())
                        .build();
        client.getHttpClient();
    }

    @Test
    public void entry() {
        assertNotNull(J4pClient.user("roland"));
        assertNotNull(J4pClient.password("s!cr!t"));
        assertNotNull(J4pClient.connectionTimeout(100));
        assertNotNull(J4pClient.expectContinue(true));
        assertNotNull(J4pClient.tcpNoDelay(true));
        assertNotNull(J4pClient.contentCharset("utf-8"));
        assertNotNull(J4pClient.maxConnectionPoolTimeout(3000));
        assertNotNull(J4pClient.maxTotalConnections(100));
        assertNotNull(J4pClient.singleConnection());
        assertNotNull(J4pClient.pooledConnections());
        assertNotNull(J4pClient.socketBufferSize(8192));
        assertNotNull(J4pClient.socketTimeout(5000));
        assertNotNull(J4pClient.cookieStore(new BasicCookieStore()));
        assertNotNull(J4pClient.defaultMaxConnectionsPerRoute(100));
    }

    @Test
    public void testParseProxySettings_null() throws Exception {
        assertNull(J4pClientBuilder.parseProxySettings(null));
    }

    @Test
    public void proxy_stringWithUserPassHostAndPort() {
        J4pClient client =
                J4pClient
                        .url("http://localhost:8080/jolokia")
                        .proxy("http://user:pass@host:8080")
                        .build();
        client.getHttpClient();
    }

    @Test
    public void proxy_hostAndPort() {
        J4pClient client =
                J4pClient
                        .url("http://localhost:8080/jolokia")
                        .proxy("host", 8080)
                        .build();
        client.getHttpClient();
    }

    @Test
    public void proxy_hostPortUserAndPassword() {
        J4pClient client =
                J4pClient
                        .url("http://localhost:8080/jolokia")
                        .proxy("host",8080,"user","pass")
                        .build();
        client.getHttpClient();
    }
    @Test
    public void proxy_useProxyFromEnvironment() {
        J4pClient client =
                J4pClient
                        .url("http://localhost:8080/jolokia")
                        .useProxyFromEnvironment()
                        .build();
        client.getHttpClient();
    }

    @Test(expectedExceptions = J4pConnectException.class, expectedExceptionsMessageRegExp = ".*localhost:65535.*")
    public void proxy_executeNoProxy() throws MalformedObjectNameException, J4pException {
        J4pClient client =
                J4pClient
                        .url("http://localhost:8080/jolokia")
                        .proxy("localhost", 65535, "user", "pass") // most likely there is no proxy on this port
                        .build();
        J4pReadRequest readRequest = new J4pReadRequest("java.lang:type=Memory", "HeapMemoryUsage");
        client.execute(readRequest);
    }

}
