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

import java.net.http.HttpClient;
import javax.management.MalformedObjectNameException;

import org.jolokia.client.exception.J4pConnectException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.JolokiaReadRequest;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * @author roland
 * @since 23.09.11
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class JolokiaClientBuilderTest {

    @Test
    public void simple() {
        JolokiaClient client =
                new JolokiaClientBuilder()
                        .url("http://localhost:8080/jolokia")
                        .user("roland")
                        .password("s!c!r!t")
                        .connectionTimeout(100)
                        .expectContinue(false)
                        .tcpNoDelay(true)
                        .contentCharset("utf-8")
//                        .maxConnectionPoolTimeout(3000)
//                        .maxTotalConnections(500)
//                        .defaultMaxConnectionsPerRoute(500)
//                        .pooledConnections()
                        .socketBufferSize(8192)
                        .socketTimeout(5000)
//                        .cookieStore(new BasicCookieStore())
                        .build();
        HttpClient realClient = client.getHttpClient(HttpClient.class);
        assertNotNull(realClient);
    }

    @Test
    public void testParseProxySettings_null() {
        assertNull(JolokiaClientBuilder.parseProxySettings(null));
    }

    @Test
    public void proxy_stringWithUserPassHostAndPort() {
        JolokiaClient client =
                new JolokiaClientBuilder()
                        .url("http://localhost:8080/jolokia")
                        .proxy("http://user:pass@host:8080")
                        .build();
        client.getHttpClient(HttpClient.class);
    }

    @Test
    public void proxy_hostAndPort() {
        JolokiaClient client =
            new JolokiaClientBuilder()
                        .url("http://localhost:8080/jolokia")
                        .proxy("host", 8080)
                        .build();
        client.getHttpClient(HttpClient.class);
    }

    @Test
    public void proxy_hostPortUserAndPassword() {
        JolokiaClient client =
            new JolokiaClientBuilder()
                        .url("http://localhost:8080/jolokia")
                        .proxy("host",8080,"user","pass")
                        .build();
        client.getHttpClient(HttpClient.class);
    }
    @Test
    public void proxy_useProxyFromEnvironment() {
        JolokiaClient client =
            new JolokiaClientBuilder()
                        .url("http://localhost:8080/jolokia")
                        .useProxyFromEnvironment()
                        .build();
        client.getHttpClient(HttpClient.class);
    }

    @Test(expectedExceptions = J4pConnectException.class, expectedExceptionsMessageRegExp = ".*Cannot connect to http://localhost:8080/jolokia.*")
    public void proxy_executeNoProxy() throws MalformedObjectNameException, J4pException {
        JolokiaClient client =
            new JolokiaClientBuilder()
                        .url("http://localhost:8080/jolokia")
                        .proxy("localhost", 65535, "user", "pass") // most likely there is no proxy on this port
                        .build();
        JolokiaReadRequest readRequest = new JolokiaReadRequest("java.lang:type=Memory", "HeapMemoryUsage");
        client.execute(readRequest);
    }

}
