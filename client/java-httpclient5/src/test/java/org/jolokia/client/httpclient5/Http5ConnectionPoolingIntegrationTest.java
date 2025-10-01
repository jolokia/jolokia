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
package org.jolokia.client.httpclient5;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.ObjectName;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.request.AbstractClientIntegrationTest;
import org.jolokia.client.request.JolokiaSearchRequest;
import org.jolokia.client.response.JolokiaSearchResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.test.util.EnvTestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

public class Http5ConnectionPoolingIntegrationTest {

    public static final Logger LOG = LoggerFactory.getLogger(Http5ConnectionPoolingIntegrationTest.class);

    private static String jettyVersion;

    private Server jettyServer;
    private String jolokiaUrl;

    private static void checkJettyVersion() {
        try (InputStream is = AbstractClientIntegrationTest.class.getResourceAsStream("/META-INF/maven/org.eclipse.jetty/jetty-server/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                jettyVersion = props.getProperty("version");
            }
        } catch (Exception ignored) {
            jettyVersion = "unknown";
        }
    }

    final AtomicInteger requestCount = new AtomicInteger();

    @BeforeClass
    public void init() {
        checkJettyVersion();
    }

    @BeforeMethod
    public void setUp() throws Exception {
        requestCount.set(0);

        int port = EnvTestUtil.getFreePort();
        jettyServer = new Server(port);
        ServletContextHandler jettyContext = new ServletContextHandler("/");
        ServletHolder holder = new ServletHolder(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                requestCount.getAndIncrement();
                resp.getWriter().println(getJsonResponse("test"));
                resp.getWriter().close();
            }
        });
        jettyContext.addServlet(holder, "/jolokia/*");

        jettyServer.setHandler(jettyContext);
        jettyServer.start();

        jolokiaUrl = "http://localhost:" + port + "/jolokia";
        LOG.info("Started Jetty Server ({}). Jolokia available at {}", jettyVersion, jolokiaUrl);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        jettyServer.stop();
    }

    @Test
    public void testSearchParallelWithConnectionPoolException() throws Exception {
        final JolokiaClient client = createJolokiaClient(jolokiaUrl, 19);
        try {
            searchParallel(client);
            fail();
        } catch (ExecutionException executionException) {
            assertEquals(ConnectionRequestTimeoutException.class, executionException.getCause().getCause().getClass());
        }
    }

    @Test
    public void testSearchParallel() throws Exception {
        final JolokiaClient client = createJolokiaClient(jolokiaUrl, 20);
        searchParallel(client);

        assertEquals(20, requestCount.get());
    }

    private void searchParallel(JolokiaClient jolokiaClient) throws Exception {
        final ExecutorService executorService = Executors.newFixedThreadPool(20);
        final JolokiaSearchRequest j4pSearchRequest = new JolokiaSearchRequest("java.lang:type=*");

        final List<Future<Void>> requestsList = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            requestsList.add(executorService.submit(new AsyncRequest(jolokiaClient, j4pSearchRequest)));
        }

        for (Future<Void> requests : requestsList) {
            requests.get();
        }

        executorService.shutdown();
    }

    private JolokiaClient createJolokiaClient(String url, int maxTotalConnections) {
        return new JolokiaClientBuilder().url(url)
            .pooledConnections()
            .maxTotalConnections(maxTotalConnections)
            .build();
    }

    static class AsyncRequest implements Callable<Void> {
        private final JolokiaClient jolokiaClient;
        private final JolokiaSearchRequest j4pSearchRequest;

        public AsyncRequest(JolokiaClient jolokiaClient, JolokiaSearchRequest j4pSearchRequest) {
            this.jolokiaClient = jolokiaClient;
            this.j4pSearchRequest = j4pSearchRequest;
        }

        public Void call() throws Exception {
            JolokiaSearchResponse resp = jolokiaClient.execute(j4pSearchRequest);
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
