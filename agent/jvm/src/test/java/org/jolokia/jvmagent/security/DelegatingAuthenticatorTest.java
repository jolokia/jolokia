package org.jolokia.jvmagent.security;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.io.IOException;
import java.io.Writer;

import javax.net.ssl.*;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.*;

import com.sun.net.httpserver.*;
import org.jolokia.test.util.EnvTestUtil;
import org.jolokia.util.AuthorizationHeaderParser;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 27/05/15
 */
public class DelegatingAuthenticatorTest extends BaseAuthenticatorTest {

    private Server jettyServer;
    private String url;

    @DataProvider
    public static Object[][] headers() {
        return new Object[][]{{"Authorization"}, {AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER}};
    }

    @BeforeClass
    public void setup() throws Exception {
        int port = EnvTestUtil.getFreePort();
        jettyServer = new Server(port);
        Context jettyContext = new Context(jettyServer, "/");
        ServletHolder holder = new ServletHolder(createServlet());
        jettyContext.addServlet(holder, "/test/*");

        jettyServer.start();
        url = "http://localhost:" + port + "/test";
    }

    private Servlet createServlet() {
        return new HttpServlet() {
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                String auth = req.getHeader("Authorization");
                if (auth == null || !auth.equals("Bearer blub")) {
                    resp.setStatus(401);
                } else {
                    resp.setContentType("text/json");
                    Writer writer = resp.getWriter();
                    if (req.getPathInfo() != null && req.getPathInfo().contains("invalid")) {
                        writer.append("{\"Invalid JSON\"");
                    } else {
                        writer.append("{\"metadata\":{\"name\":\"roland\"},\"array\":[\"eins\",\"zwei\"]}");
                    }
                }
            }
        };
    }


    @Test
    public void noAuth() {
        DelegatingAuthenticator authenticator = new DelegatingAuthenticator("jolokia",url,"json:metadata/name",false);

        Headers respHeader = new Headers();
        HttpExchange ex = createHttpExchange(respHeader);

        Authenticator.Result result = authenticator.authenticate(ex);
        assertNotNull(result);
        assertTrue(result instanceof Authenticator.Failure);
        assertEquals(((Authenticator.Failure) result).getResponseCode(), 401);
    }

    @Test(dataProvider = "headers")
    public void withAuth(String header) {
        SSLSocketFactory sFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        HostnameVerifier hVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        try {
            String[] data = {
                    "json:metadata/name", "roland",
                    "json:array/0", "eins",
                    "empty:", "",
                    null, ""
            };
            for (int i = 0; i < data.length; i += 2) {
                HttpPrincipal principal = executeAuthCheck(data[i], header);
                assertEquals(principal.getRealm(), "jolokia");
                assertEquals(principal.getUsername(), data[i+1]);
            }
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(sFactory);
            HttpsURLConnection.setDefaultHostnameVerifier(hVerifier);
        }
    }

    private HttpPrincipal executeAuthCheck(String pSpec, String header) {
        DelegatingAuthenticator authenticator = new DelegatingAuthenticator("jolokia", url, pSpec, true);

        Headers respHeader = new Headers();
        HttpExchange ex = createHttpExchange(respHeader, header, "Bearer blub");

        Authenticator.Result result = authenticator.authenticate(ex);
        assertNotNull(result);
        Authenticator.Success success = (Authenticator.Success) result;
        return success.getPrincipal();
    }

    @Test
    public void invalidProtocol() {
        DelegatingAuthenticator authenticator = new DelegatingAuthenticator("jolokia","ftp://ftp.redhat.com",null,false);

        Authenticator.Result result = authenticator.authenticate(createHttpExchange(new Headers()));
        Authenticator.Failure failure = (Authenticator.Failure) result;
        assertEquals(failure.getResponseCode(),401);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*blub.*")
    public void invalidExtractor() {
        new DelegatingAuthenticator("jolokia","http://www.redhat.com","blub:bla",false);
    }

    @Test
    public void ioException() {
        String wrongUrl = "http://0.0.0.2:80";
        DelegatingAuthenticator authenticator = new DelegatingAuthenticator("jolokia",wrongUrl,null,false);
        HttpExchange exchange = createHttpExchange(new Headers());
        Authenticator.Result result = authenticator.authenticate(exchange);
        Authenticator.Failure failure = (Authenticator.Failure) result;
        assertEquals(failure.getResponseCode(),503);
        String error = exchange.getResponseHeaders().getFirst("X-Error-Details");
        assertTrue(error.contains("http://0.0.0.2:80"));
    }

    @Test
    public void invalidPath() {
        String  data[] = new String[] { "json:never/find/me", "never",
                                        "json:metadata/name/yet/deeper", "deeper" };
        for (int i = 0; i < data.length; i +=2) {
            DelegatingAuthenticator authenticator = new DelegatingAuthenticator("jolokia", url, data[i], false);
            HttpExchange exchange = createHttpExchange(new Headers(), "Authorization", "Bearer blub");
            Authenticator.Result result = authenticator.authenticate(exchange);
            Authenticator.Failure failure = (Authenticator.Failure) result;
            assertEquals(failure.getResponseCode(), 400);
            String error = exchange.getResponseHeaders().getFirst("X-Error-Details");
            assertTrue(error.contains(data[i+1]));
        }
    }

    @Test
    public void invalidJson() {
        DelegatingAuthenticator authenticator = new DelegatingAuthenticator("jolokia", url + "/invalid","json:metadata/name", false);
        HttpExchange exchange = createHttpExchange(new Headers(), "Authorization", "Bearer blub");
        Authenticator.Result result = authenticator.authenticate(exchange);
        Authenticator.Failure failure = (Authenticator.Failure) result;
        assertEquals(failure.getResponseCode(), 422);
        String error = exchange.getResponseHeaders().getFirst("X-Error-Details");
        assertTrue(error.contains("Invalid JSON"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*blub.*")
    public void malformedUrl() {
        new DelegatingAuthenticator("jolokia","blub//://bla",null,false);
    }

    @Test(dataProvider = "headers")
    public void emptySpec(String header){

        HttpPrincipal principal = executeAuthCheck("empty:", header);
        assertEquals(principal.getRealm(), "jolokia");
        assertEquals(principal.getUsername(), "");
    }

    @AfterClass
    public void tearDown() throws Exception {
        jettyServer.stop();
    }
}
