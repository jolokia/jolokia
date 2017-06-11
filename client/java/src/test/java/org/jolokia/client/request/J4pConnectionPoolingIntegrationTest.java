package org.jolokia.client.request;

/*
 * Copyright 2009-2017 Baris Cubukcuoglu
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.jolokia.client.J4pClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.AssertJUnit.*;

public class J4pConnectionPoolingIntegrationTest {

    private WireMockServer wireMockServer;

    @BeforeMethod
    public void setUp() throws Exception {
        wireMockServer = new WireMockServer(Options.DYNAMIC_PORT);
        wireMockServer.start();
    }

    @Test
    public void testSearchParallelWithConnectionPoolException() throws Exception {
        configureFor("localhost", wireMockServer.port());
        final J4pClient j4pClient = createJ4pClient("http://localhost:" + wireMockServer.port() + "/test", 20, 2);
        try {
            searchParallel(j4pClient);
            fail();
        } catch (ExecutionException executionException) {
            assertEquals(ConnectionPoolTimeoutException.class, executionException.getCause().getCause().getClass());
        }

    }

    @Test
    public void testSearchParallel() throws Exception {
        configureFor("localhost", wireMockServer.port());
        final J4pClient j4pClient = createJ4pClient("http://localhost:" + wireMockServer.port() + "/test", 20, 20);
        searchParallel(j4pClient);

        verify(20, getRequestedFor(urlPathMatching("/test/([a-z]*)")));
    }

    private void searchParallel(J4pClient j4pClient) throws Exception {
        stubFor(get(urlPathMatching("/test/([a-z]*)")).willReturn(aResponse().withFixedDelay(1000).withBody(getJsonResponse("test"))));

        final ExecutorService executorService = Executors.newFixedThreadPool(20);
        final J4pSearchRequest j4pSearchRequest = new J4pSearchRequest("java.lang:type=*");

        final List<Future<Void>> requestsList = new ArrayList<Future<Void>>();

        for (int i = 0; i < 20; i++) {
            requestsList.add(executorService.submit(new AsyncRequest(j4pClient, j4pSearchRequest)));
        }

        for (Future<Void> requests : requestsList) {
            requests.get();
        }

        executorService.shutdown();
    }


    @AfterMethod
    public void tearDown() throws Exception {
        wireMockServer.stop();
    }

    private J4pClient createJ4pClient(String url, int maxTotalConnections, int connectionsPerRoute) {
        return J4pClient.url(url)
                .pooledConnections()
                .maxTotalConnections(maxTotalConnections)
                .defaultMaxConnectionsPerRoute(connectionsPerRoute)
                .build();
    }

    static class AsyncRequest implements Callable<Void> {
        private final J4pClient j4pClient;
        private final J4pSearchRequest j4pSearchRequest;

        public AsyncRequest(J4pClient j4pClient, J4pSearchRequest j4pSearchRequest) {
            this.j4pClient = j4pClient;
            this.j4pSearchRequest = j4pSearchRequest;
        }

        public Void call() throws Exception {
            J4pSearchResponse resp = j4pClient.execute(j4pSearchRequest);
            assertNotNull(resp);
            List<ObjectName> names = resp.getObjectNames();
            assertTrue(names.contains(new ObjectName("java.lang:type=Memory")));
            return null;
        }
    }

    private String getJsonResponse(String message) {
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode node = objectMapper.createObjectNode();

        final ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add("java.lang:type=Memory");
        node.putArray("value").addAll(arrayNode);

        node.put("status", 200);
        node.put("timestamp", 1244839118);

        return node.toString();
    }
}
