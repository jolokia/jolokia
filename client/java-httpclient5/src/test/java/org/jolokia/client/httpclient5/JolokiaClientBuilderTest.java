/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client.httpclient5;

import java.io.IOException;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class JolokiaClientBuilderTest {

    @Test
    public void simple() throws IOException {
        JolokiaClient client =
            new JolokiaClientBuilder()
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
                .build();
        HttpClient realClient = client.getHttpClient(HttpClient.class);
        assertNotNull(realClient);
        client.close();
    }

    @Test
    public void withBuilderCustomizer() throws IOException {
        final boolean[] called = {false};
        JolokiaClient client = new JolokiaClientBuilder().url("http://localhost:8080/jolokia")
            .withCustomizer(HttpClientBuilder.class, (HttpClientBuilder builder) -> {
                builder.setUserAgent("My Client based on HttpClient 5");
                called[0] = true;
            }).build();
        HttpClient realClient = client.getHttpClient(HttpClient.class);
        assertNotNull(realClient);
        assertTrue(called[0]);
        client.close();
    }

}
