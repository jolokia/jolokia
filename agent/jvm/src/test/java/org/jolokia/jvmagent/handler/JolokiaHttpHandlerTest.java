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
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.easymock.EasyMock;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.restrictor.*;
import org.jolokia.util.LogHandler;
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


    @BeforeMethod
    public void setup() {
        handler = new JolokiaHttpHandler(getConfig());
        handler.start(false);
    }

    @AfterMethod
    public void tearDown() {
        handler.stop();
    }

    @Test
    public void testCallbackGet() throws IOException, URISyntaxException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?callback=data");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);

        handler.doHandle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");
        String result = out.toString("utf-8");
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
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);

        handler.doHandle(exchange);

        assertEquals(header.getFirst("content-type"),expected + "; charset=utf-8");
    }

    @Test
    public void testInvalidCallbackGetStreaming() throws IOException, URISyntaxException, ParseException {
        checkInvalidCallback(true);
    }

    @Test
    public void testInvalidCallbackGetNonStreaming() throws IOException, URISyntaxException, ParseException {
        checkInvalidCallback(false);
    }

    private void checkInvalidCallback(boolean streaming) throws URISyntaxException, IOException, ParseException {
        JolokiaHttpHandler handler = new JolokiaHttpHandler(getConfig(ConfigKey.SERIALIZE_EXCEPTION, Boolean.toString(streaming)));
        handler.start(false);

        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?callback=evilCallback();data");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);

        handler.doHandle(exchange);

        assertEquals(header.getFirst("content-type"),"text/plain; charset=utf-8");
        String result = out.toString("utf-8");
        JSONObject resp = (JSONObject) new JSONParser().parse(result);
        assertTrue(resp.containsKey("error"));
        assertEquals(resp.get("error_type"), IllegalArgumentException.class.getName());
        assertTrue(((String) resp.get("error")).contains("callback"));
        assertFalse(((String) resp.get("error")).contains("evilCallback"));

        handler.stop();
    }


    @Test
    public void testCallbackPost() throws URISyntaxException, IOException, java.text.ParseException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia?callback=data",
                                                "Content-Type","text/plain; charset=UTF-8",
                                                "Origin",""
                                               );

        prepareMemoryPostReadRequest(exchange);
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);

        handler.doHandle(exchange);

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
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);
        handler.doHandle(exchange);

        JSONObject resp = (JSONObject) new JSONParser().parse(out.toString());
        assertTrue(resp.containsKey("error"));
        assertEquals(resp.get("error_type"), IllegalArgumentException.class.getName());
        assertTrue(((String) resp.get("error")).contains("PUT"));
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*not.*started.*")
    public void handlerNotStarted() throws URISyntaxException, IOException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(getConfig());
        newHandler.doHandle(prepareExchange("http://localhost:8080/"));

    }

    @Test
    public void customRestrictor() throws URISyntaxException, IOException, ParseException {
        System.setProperty("jolokia.test1.policy.location","access-restrictor.xml");
        System.setProperty("jolokia.test2.policy.location","access-restrictor");
        for (String[] params : new String[][] {
                {"classpath:/access-restrictor.xml","not allowed"},
                {"file:///not-existing.xml","No access"},
                {"classpath:/${prop:jolokia.test1.policy.location}", "not allowed"},
                {"classpath:/${prop:jolokia.test2.policy.location}.xml", "not allowed"}
        }) {
            Configuration config = getConfig(ConfigKey.POLICY_LOCATION,params[0]);
            JSONObject resp = simpleMemoryGetReadRequest(config);
            assertTrue(resp.containsKey("error"));
            assertTrue(((String) resp.get("error")).contains(params[1]));
        }
    }

    @Test
    public void customTestRestrictorTrue() throws URISyntaxException, IOException, ParseException {

        Configuration config = getConfig(ConfigKey.RESTRICTOR_CLASS,  AllowAllRestrictor.class.getName());
        JSONObject resp = simpleMemoryGetReadRequest(config);
        assertFalse(resp.containsKey("error"));

    }

    @Test
    public void customTestRestrictorFalse() throws URISyntaxException, IOException, ParseException {
        Configuration config = getConfig(ConfigKey.RESTRICTOR_CLASS, DenyAllRestrictor.class.getName());
        JSONObject resp = simpleMemoryGetReadRequest(config);
        assertTrue(resp.containsKey("error"));
        assertTrue(((String) resp.get("error")).contains("No access"));
    }

    @Test
    public void customTestRestrictorWithConfigTrue() throws URISyntaxException, IOException, ParseException {
        Configuration config = getConfig(
                ConfigKey.RESTRICTOR_CLASS, TestRestrictorWithConfig.class.getName(),
                ConfigKey.POLICY_LOCATION, "true"
        );
        JSONObject resp = simpleMemoryGetReadRequest(config);
        assertFalse(resp.containsKey("error"));
    }

    @Test
    public void customTestRestrictorWithConfigFalse() throws URISyntaxException, IOException, ParseException {
        Configuration config = getConfig(
                ConfigKey.RESTRICTOR_CLASS, TestRestrictorWithConfig.class.getName(),
                ConfigKey.POLICY_LOCATION, "false"
        );
        JSONObject resp = simpleMemoryGetReadRequest(config);
        assertTrue(resp.containsKey("error"));
        assertTrue(((String) resp.get("error")).contains("No access"));
    }

    @Test
    public void restrictorWithNoReverseDnsLookup() throws URISyntaxException, IOException, ParseException {
        Configuration config = getConfig(
                ConfigKey.RESTRICTOR_CLASS, TestReverseDnsLookupRestrictor.class.getName(),
                ConfigKey.ALLOW_DNS_REVERSE_LOOKUP, "false");
        InetSocketAddress address = new InetSocketAddress(8080);
        TestReverseDnsLookupRestrictor.expectedRemoteHostsToCheck = new String[] { address.getAddress().getHostAddress() };
        JSONObject resp = simpleMemoryGetReadRequest(config);
        assertFalse(resp.containsKey("error"));
    }

    @Test
    public void restrictorWithReverseDnsLookup() throws URISyntaxException, IOException, ParseException {
        Configuration config = getConfig(
                ConfigKey.RESTRICTOR_CLASS, TestReverseDnsLookupRestrictor.class.getName(),
                ConfigKey.ALLOW_DNS_REVERSE_LOOKUP, "true");
        InetSocketAddress address = new InetSocketAddress(8080);
        TestReverseDnsLookupRestrictor.expectedRemoteHostsToCheck = new String[] {
                address.getHostName(),
                address.getAddress().getHostAddress()
        };
        JSONObject resp = simpleMemoryGetReadRequest(config);
        assertFalse(resp.containsKey("error"));
    }

    @Test
    public void customLogHandler1() throws Exception {
        JolokiaHttpHandler handler = new JolokiaHttpHandler(getConfig(), new CustomLogHandler());
        handler.start(false);
        handler.stop();
        assertTrue(CustomLogHandler.infoCount  > 0);
    }

    @Test
    public void customLogHandler2() throws Exception {
        CustomLogHandler.infoCount = 0;
        JolokiaHttpHandler handler = new JolokiaHttpHandler(getConfig(ConfigKey.LOGHANDLER_CLASS,CustomLogHandler.class.getName()));
        handler.start(false);
        handler.stop();
        assertTrue(CustomLogHandler.infoCount > 0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidCustomLogHandler() throws Exception {
        new JolokiaHttpHandler(getConfig(ConfigKey.LOGHANDLER_CLASS,InvalidLogHandler.class.getName()));
    }

    @Test
    public void simlePostRequestWithCors() throws URISyntaxException, IOException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia",
                                                "Content-Type","text/plain; charset=UTF-8",
                                                "Origin","http://localhost:8080/"
                                               );

        prepareMemoryPostReadRequest(exchange);
        Headers header = new Headers();
        prepareResponse(handler, exchange, header);
        handler.doHandle(exchange);

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
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);
        handler.doHandle(exchange);
        assertEquals(header.getFirst("Access-Control-Allow-Origin"),"http://localhost:8080/");
        assertEquals(header.getFirst("Access-Control-Allow-Headers"),"X-Bla, X-Blub");
        assertNotNull(header.getFirst("Access-Control-Max-Age"));
    }

    @Test
    public void usingStreamingJSON() throws IOException, URISyntaxException, ParseException {
        Configuration config = getConfig(ConfigKey.STREAMING, "true");
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(config);
        newHandler.start(false);

        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/list?maxDepth=1");
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(newHandler, exchange, header);
        newHandler.doHandle(exchange);

        String result = out.toString("utf-8");

        assertNull(header.getFirst("Content-Length"));
        JSONObject resp = (JSONObject) new JSONParser().parse(result);
        assertTrue(resp.containsKey("value"));
    }

    private HttpExchange prepareExchange(String pUri) throws URISyntaxException {
        return prepareExchange(pUri,"Origin","");
    }

    private HttpExchange prepareExchange(String pUri,String ... pHeaders) throws URISyntaxException {
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

    private JSONObject simpleMemoryGetReadRequest(Configuration config) throws URISyntaxException, IOException, ParseException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(config);
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);
        newHandler.start(false);
        try {
            newHandler.doHandle(exchange);
        } finally {
            newHandler.stop();
        }
        return (JSONObject) new JSONParser().parse(out.toString());
    }

    private ByteArrayOutputStream prepareResponse(JolokiaHttpHandler handler, HttpExchange exchange, Headers header) throws IOException {
        expect(exchange.getResponseHeaders()).andReturn(header).anyTimes();
        exchange.sendResponseHeaders(anyInt(),anyLong());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        expect(exchange.getResponseBody()).andReturn(out);
        replay(exchange);
        return out;
    }

    private static boolean debugToggle = false;
    public Configuration getConfig(Object ... extra) {
        ArrayList list = new ArrayList();
        list.add(ConfigKey.AGENT_CONTEXT);
        list.add("/jolokia");
        list.add(ConfigKey.DEBUG);
        list.add(debugToggle ? "true" : "false");
        list.add(ConfigKey.AGENT_ID);
        list.add(UUID.randomUUID().toString());
        for (Object e : extra) {
            list.add(e);
        }
        Configuration config = new Configuration(list.toArray());
        debugToggle = !debugToggle;
        return config;
    }

    public static class CustomLogHandler implements LogHandler {

        private static int debugCount, infoCount, errorCount;

        public CustomLogHandler() {
            debugCount = 0;
            infoCount = 0;
            errorCount = 0;
        }

        @Override
        public void debug(String message) {
            debugCount++;
        }

        @Override
        public void info(String message) {
            infoCount++;
        }

        @Override
        public void error(String message, Throwable t) {
            errorCount++;
        }
    }

    private class InvalidLogHandler implements LogHandler {

        @Override
        public void debug(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void error(String message, Throwable t) {
        }
    }
}
