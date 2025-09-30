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

//import com.github.tomakehurst.wiremock.WireMockServer;
//import com.github.tomakehurst.wiremock.core.Options;
//import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
//import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory;
import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.response.JolokiaSearchResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.ObjectName;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

//import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.AssertJUnit.*;

public class J4pConnectionPoolingIntegrationTest {

//    private WireMockServer wireMockServer;

    @BeforeMethod
    public void setUp() {
//        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort().disableOptimizeXmlFactoriesLoading(true));
//        wireMockServer.start();
    }

    @Test
    public void testSearchParallelWithConnectionPoolException() throws Exception {
//        configureFor("localhost", wireMockServer.port());
//        final J4pClient j4pClient = createJ4pClient("http://localhost:" + wireMockServer.port() + "/test", 20, 2);
//        try {
//            searchParallel(j4pClient);
//            fail();
//        } catch (ExecutionException executionException) {
//            assertEquals(HttpConnectTimeoutException.class, executionException.getCause().getCause().getClass());
//        }

    }

    @Test
    public void testSearchParallel() throws Exception {
//        configureFor("localhost", wireMockServer.port());
//        final J4pClient j4pClient = createJ4pClient("http://localhost:" + wireMockServer.port() + "/test", 20, 20);
//        searchParallel(j4pClient);
//
//        verify(20, getRequestedFor(urlPathMatching("/test/([a-z.:=*/]*)")));
    }

    private void searchParallel(J4pClient j4pClient) throws Exception {
//        stubFor(get(urlPathMatching("/test/([a-z.:=*/]*)")).willReturn(aResponse().withFixedDelay(1000).withBody(getJsonResponse("test"))));

        final ExecutorService executorService = Executors.newFixedThreadPool(20);
        final JolokiaSearchRequest j4pSearchRequest = new JolokiaSearchRequest("java.lang:type=*");

        final List<Future<Void>> requestsList = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            requestsList.add(executorService.submit(new AsyncRequest(j4pClient, j4pSearchRequest)));
        }

        for (Future<Void> requests : requestsList) {
            requests.get();
        }

        executorService.shutdown();
    }


    @AfterMethod
    public void tearDown() {
//        wireMockServer.stop();
    }

    private J4pClient createJ4pClient(String url, int maxTotalConnections, int connectionsPerRoute) {
        return new J4pClientBuilder().url(url)
//                .pooledConnections()
//                .maxTotalConnections(maxTotalConnections)
//                .defaultMaxConnectionsPerRoute(connectionsPerRoute)
                .build();
    }

    static class AsyncRequest implements Callable<Void> {
        private final J4pClient j4pClient;
        private final JolokiaSearchRequest j4pSearchRequest;

        public AsyncRequest(J4pClient j4pClient, JolokiaSearchRequest j4pSearchRequest) {
            this.j4pClient = j4pClient;
            this.j4pSearchRequest = j4pSearchRequest;
        }

        public Void call() throws Exception {
            JolokiaSearchResponse resp = j4pClient.execute(j4pSearchRequest);
            assertNotNull(resp);
            List<ObjectName> names = resp.getObjectNames();
            assertTrue(names.contains(new ObjectName("java.lang:type=Memory")));
            return null;
        }
    }

    private String getJsonResponse(String message) {
        JSONObject result = new JSONObject();
        JSONArray value = new JSONArray();
        value.add("java.lang:type=Memory");
        result.put("value", value);
        result.put("status", 200);
        result.put("timestamp", 1244839118);

        return result.toJSONString();
    }
}
