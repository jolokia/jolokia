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

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCookieStore;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;

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
                        .pooledConnections()
                        .socketBufferSize(8192)
                        .socketTimeout(5000)
                        .cookieStore(new BasicCookieStore())
                        .build();
        HttpClient hc = client.getHttpClient();
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
    }

}
