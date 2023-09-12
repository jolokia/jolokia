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

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;

import javax.net.ssl.*;

import com.sun.net.httpserver.*;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.StatusCodes;
import org.jolokia.server.core.osgi.security.AuthorizationHeaderParser;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.annotations.*;
import org.xnio.channels.Channels;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 27/05/15
 */
public class DelegatingAuthenticatorTest extends BaseAuthenticatorTest {

    private Undertow undertowServer;
    private String url;

    @DataProvider
    public static Object[][] headers() {
        return new Object[][]{{"Authorization"}, { AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER}};
    }

    @BeforeClass
    public void setup() throws Exception {
        int port = EnvTestUtil.getFreePort();

        PathHandler path = Handlers.path();
        undertowServer = Undertow.builder()
                .addHttpListener(port, "127.0.0.1")
                .setHandler(path)
                .build();

        path.addPrefixPath("/test", createHandler());

        undertowServer.start();
        url = "http://127.0.0.1:" + port + "/test";
    }

    private HttpHandler createHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange ex) throws Exception {
                HeaderMap headers = ex.getRequestHeaders();
                HeaderValues values = headers.get(io.undertow.util.Headers.AUTHORIZATION);
                String auth = values == null ? null : values.getFirst();
                if (auth == null || !auth.equals("Bearer blub")) {
                    ex.setStatusCode(StatusCodes.UNAUTHORIZED);
                    ex.endExchange();
                } else {
                    ex.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "text/json");

                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                         OutputStreamWriter writer = new OutputStreamWriter(outputStream, UTF_8)) {

                        String requestUri = ex.getRequestURI();
                        if (requestUri != null && requestUri.contains("invalid")) {
                            writer.append("{\"Invalid JSON\"");
                        } else {
                            writer.append("{\"metadata\":{\"name\":\"roland\"},\"array\":[\"eins\",\"zwei\"]}");
                        }
                        writer.close();

                        ByteBuffer output = ByteBuffer.wrap(outputStream.toByteArray());
                        ex.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_LENGTH, String.valueOf(output.limit()));
                        Channels.writeBlocking(ex.getResponseChannel(), output);
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
    public void tearDown() {
        undertowServer.stop();
    }
}
