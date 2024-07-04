package org.jolokia.jvmagent.handler;

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

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.management.MalformedObjectNameException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.easymock.EasyMock;
import org.jolokia.server.core.backend.BackendManager;
import org.jolokia.server.core.backend.RequestDispatcher;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.http.HttpRequestHandler;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.annotations.*;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 24.10.10
 */
public class JolokiaHttpHandlerTest {

    private JolokiaHttpHandler handler;
    private RequestDispatcher requestDispatcher;


    @BeforeMethod
    public void setup() throws MalformedObjectNameException, NoSuchFieldException, IllegalAccessException {
        TestJolokiaContext ctx = getContext();
        requestDispatcher = new TestRequestDispatcher.Builder()
                .request(new JolokiaRequestBuilder(RequestType.READ, "java.lang:type=Memory").attribute("HeapMemoryUsage").build())
                .andReturnMapValue("used",4711L).build();
        handler = new JolokiaHttpHandler(ctx);
        // Not optimal since diving into internal, but the overall test is not very
        // optimal.
        injectRequestDispatcher(handler,requestDispatcher);
    }

    // Quick fix for replacing the request dispatcher
    private void injectRequestDispatcher(JolokiaHttpHandler pHandler, RequestDispatcher pRequestDispatcher) throws NoSuchFieldException, IllegalAccessException {
        Field field = pHandler.getClass().getDeclaredField("requestHandler");
        field.setAccessible(true);
        HttpRequestHandler rHandler = (HttpRequestHandler) field.get(pHandler);
        field = HttpRequestHandler.class.getDeclaredField("backendManager");
        field.setAccessible(true);
        BackendManager bManager = (BackendManager) field.get(rHandler);
        field = BackendManager.class.getDeclaredField("requestDispatcher");
        field.setAccessible(true);
        field.set(bManager,pRequestDispatcher);
    }

    @AfterMethod
    public void tearDown() {
        if (handler != null) {
            handler = null;
        }
    }

    @Test
    public void testCallbackGet() throws IOException, URISyntaxException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?callback=data");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");
        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.endsWith("});"));
        assertTrue(result.startsWith("data({"));
    }


    @Test
    public void testInvalidMimeType() throws IOException, URISyntaxException {
        checkMimeType("text/html", "text/plain");
    }

    @Test
    public void testMimeTypeApplicationJson() throws IOException, URISyntaxException {
        checkMimeType("application/json", "application/json");
    }

    private void checkMimeType(String given, String expected) throws IOException, URISyntaxException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?mimeType=" + given);

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),expected + "; charset=utf-8");
    }

    @Test
    public void testInvalidCallbackGetStreaming() throws IOException, URISyntaxException, JSONException {
        checkInvalidCallback(true);
    }

    @Test
    public void testInvalidCallbackGetNonStreaming() throws IOException, URISyntaxException, JSONException {
        checkInvalidCallback(false);
    }

    private void checkInvalidCallback(boolean streaming) throws URISyntaxException, IOException, JSONException {
        JolokiaContext ctx = new TestJolokiaContext.Builder()
            .config(ConfigKey.SERIALIZE_EXCEPTION, Boolean.toString(streaming))
            .build();
        JolokiaHttpHandler handler = new JolokiaHttpHandler(ctx);

        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?callback=evilCallback();data");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);
        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/plain; charset=utf-8");
        String result = out.toString(StandardCharsets.UTF_8);
        JSONObject resp = JSONAware.parse(new StringReader(result)).getObject();
        assertTrue(resp.has("error"));
        assertEquals(resp.get("error_type"), IllegalArgumentException.class.getName());
        assertTrue(((String) resp.get("error")).contains("callback"));
        assertFalse(((String) resp.get("error")).contains("evilCallback"));
    }


    @Test
    public void testCallbackPost() throws URISyntaxException, IOException, java.text.ParseException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia?callback=data",
                                                "Content-Type","text/plain; charset=UTF-8",
                                                "Origin",""
                                               );

        prepareMemoryPostReadRequest(exchange);
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");
        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.endsWith("});"));
        assertTrue(result.startsWith("data({"));
        assertTrue(result.contains("\"used\""));

        assertEquals(header.getFirst("Cache-Control"),"no-cache");
        assertEquals(header.getFirst("Pragma"),"no-cache");
        SimpleDateFormat rfc1123Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        rfc1123Format.setTimeZone(TimeZone.getTimeZone("GMT"));

        String expires = header.getFirst("Expires");
        String date = header.getFirst("Date");

        Date parsedExpires = rfc1123Format.parse(expires);
        Date parsedDate = rfc1123Format.parse(date);
        assertTrue(parsedExpires.before(parsedDate) || parsedExpires.equals(parsedDate));
        Date now = new Date();
        assertTrue(parsedExpires.before(now) || parsedExpires.equals(now));
    }

    @Test
    public void invalidMethod() throws URISyntaxException, IOException, JSONException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("PUT");
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);
        handler.handle(exchange);

        JSONObject resp = JSONAware.parse(new StringReader(out.toString())).getObject();
        assertTrue(resp.has("error"));
        assertEquals(resp.get("error_type"), IllegalArgumentException.class.getName());
        assertTrue(((String) resp.get("error")).contains("PUT"));
    }

    @Test
    public void simlePostRequestWithCors() throws URISyntaxException, IOException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia",
                                                "Content-Type","text/plain; charset=UTF-8",
                                                "Origin","http://localhost:8080/"
                                               );

        prepareMemoryPostReadRequest(exchange);
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"), "text/plain; charset=utf-8");
        assertEquals(header.getFirst("Access-Control-Allow-Origin"),"http://localhost:8080/");
    }

    private void prepareMemoryPostReadRequest(HttpExchange pExchange) {
        expect(pExchange.getRequestMethod()).andReturn("POST");
        String response = "{\"mbean\":\"java.lang:type=Memory\",\"attribute\":\"HeapMemoryUsage\",\"type\":\"read\"}";
        byte[] buf = response.getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(buf);
        expect(pExchange.getRequestBody()).andReturn(is);
    }

    @Test
    public void preflightCheck() throws URISyntaxException, IOException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/",
                                                "Origin","http://localhost:8080/",
                                                "Access-Control-Request-Headers","X-Bla, X-Blub");
        expect(exchange.getRequestMethod()).andReturn("OPTIONS");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);
        handler.handle(exchange);
        assertEquals(header.getFirst("Access-Control-Allow-Origin"),"http://localhost:8080/");
        assertEquals(header.getFirst("Access-Control-Allow-Headers"),"X-Bla, X-Blub");
        assertNotNull(header.getFirst("Access-Control-Max-Age"));
    }

    @Test
    public void usingStreamingJSON() throws IOException, URISyntaxException, JSONException, NoSuchFieldException, IllegalAccessException {
        handler = new JolokiaHttpHandler(getContext(ConfigKey.STREAMING, "true"));
        injectRequestDispatcher(handler,requestDispatcher);

        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);
        handler.doHandle(exchange);

        String result = out.toString(StandardCharsets.UTF_8);

        assertNull(header.getFirst("Content-Length"));
        JSONObject resp = JSONAware.parse(new StringReader(result)).getObject();
        assertTrue(resp.has("value"));
    }

    private HttpExchange prepareExchange(String pUri) throws URISyntaxException {
        return prepareExchange(pUri,"Origin","");
    }

    static HttpExchange prepareExchange(String pUri,String ... pHeaders) throws URISyntaxException {
        HttpExchange exchange = EasyMock.createMock(MockableHttpExchange.class);
        URI uri = new URI(pUri);
        expect(exchange.getRequestURI()).andReturn(uri);
        expect(exchange.getRemoteAddress()).andReturn(new InetSocketAddress(8080));
        expect(exchange.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andStubReturn(null);
        Headers headers = new Headers();
        expect(exchange.getRequestHeaders()).andReturn(headers).anyTimes();
        for (int i = 0; i < pHeaders.length; i += 2) {
            headers.set(pHeaders[i], pHeaders[i + 1]);
        }
        return exchange;
    }

    static ByteArrayOutputStream prepareResponse(HttpExchange exchange, Headers header) throws IOException {
        expect(exchange.getResponseHeaders()).andReturn(header).anyTimes();
        exchange.sendResponseHeaders(anyInt(),anyLong());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        expect(exchange.getResponseBody()).andReturn(out);
        replay(exchange);
        return out;
    }

    private static boolean debugToggle = false;
    public TestJolokiaContext getContext(Object... extra) {
        List<Object> list = new ArrayList<>();
        list.add(ConfigKey.AGENT_CONTEXT);
        list.add("/jolokia");
        list.add(ConfigKey.DEBUG);
        list.add(debugToggle ? "true" : "false");
        list.add(ConfigKey.AGENT_ID);
        list.add(UUID.randomUUID().toString());
        Collections.addAll(list, extra);
        debugToggle = !debugToggle;
        return new TestJolokiaContext.Builder()
                .config(list.toArray())
                .services(Serializer.class,new TestSerializer())
                .build();
    }

    public abstract static class MockableHttpExchange extends HttpExchange {
    }

}
