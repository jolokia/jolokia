package org.jolokia.jvmagent;

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
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.management.JMException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.easymock.EasyMock;
import org.jolokia.config.ConfigKey;
import org.jolokia.util.TestJolokiaContext;
import org.jolokia.util.TestRequestDispatcher;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 24.10.10
 */
public class JolokiaHttpHandlerTest {

    private JolokiaHttpHandler handler;
    private TestJolokiaContext ctx;
    private TestRequestDispatcher requestDispatcher;


    @BeforeMethod
    public void setup() {
        ctx = getContext();
        requestDispatcher = new TestRequestDispatcher(ctx);
        handler = new JolokiaHttpHandler(ctx, requestDispatcher);
        handler.start(false);
    }

    @AfterMethod
    public void tearDown() throws JMException {
        if (handler != null) {
            handler.stop();
            requestDispatcher.destroy();
            ctx.destroy();
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
        String result = out.toString("utf-8");
        assertTrue(result.endsWith("});"));
        assertTrue(result.startsWith("data({"));
    }

    @Test
    public void testCallbackPost() throws URISyntaxException, IOException, java.text.ParseException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia?callback=data",
                                                "Content-Type","text/plain; charset=UTF-8",
                                                "Origin",null
                                               );

        // Simple GET method
        prepareMemoryPostReadRequest(exchange);
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");
        String result = out.toString("utf-8");
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
    public void invalidMethod() throws URISyntaxException, IOException, ParseException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("PUT");
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(exchange, header);
        handler.handle(exchange);

        JSONObject resp = (JSONObject) new JSONParser().parse(out.toString());
        assertTrue(resp.containsKey("error"));
        assertEquals(resp.get("error_type"),IllegalArgumentException.class.getName());
        assertTrue(((String) resp.get("error")).contains("PUT"));
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*not.*started.*")
    public void handlerNotStarted() throws URISyntaxException, IOException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(getContext(), requestDispatcher);
        newHandler.handle(prepareExchange("http://localhost:8080/"));

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

    private void prepareMemoryPostReadRequest(HttpExchange pExchange) throws UnsupportedEncodingException {
        expect(pExchange.getRequestMethod()).andReturn("POST");
        String response = "{\"mbean\":\"java.lang:type=Memory\",\"attribute\":\"HeapMemoryUsage\",\"type\":\"read\"}";
        byte[] buf = response.getBytes("utf-8");
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
        assertNotNull(header.getFirst("Access-Control-Allow-Max-Age"));
    }

    private HttpExchange prepareExchange(String pUri) throws URISyntaxException {
        return prepareExchange(pUri,"Origin",null);
    }

    static HttpExchange prepareExchange(String pUri,String ... pHeaders) throws URISyntaxException {
        HttpExchange exchange = EasyMock.createMock(HttpExchange.class);
        URI uri = new URI(pUri);
        expect(exchange.getRequestURI()).andReturn(uri);
        expect(exchange.getRemoteAddress()).andReturn(new InetSocketAddress(8080));
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
        ArrayList list = new ArrayList();
        list.add(ConfigKey.AGENT_CONTEXT);
        list.add("/jolokia");
        list.add(ConfigKey.DEBUG);
        list.add(debugToggle ? "true" : "false");
        for (Object e : extra) {
            list.add(e);
        }
        debugToggle = !debugToggle;
        return new TestJolokiaContext.Builder()
                .config(list.toArray())
                .build();
    }
}
