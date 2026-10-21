/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.jvmagent.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;

public class ConfigEndpointTest {

    @Test
    public void handleGetConfig() throws IOException {
        // Happy path test
        Filter f = new ConfigEndpointFilter("/jolokia");

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/jolokia/config"));
        expect(exchange.getRequestMethod()).andReturn("GET").anyTimes();

        Filter.Chain chain = createMock(Filter.Chain.class);
        chain.doFilter(exchange);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);
    }

    @Test
    public void handleGetConfigWithRootContext() throws IOException {
        Filter f = new ConfigEndpointFilter("/");

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/config"));
        expect(exchange.getRequestMethod()).andReturn("GET").anyTimes();

        Filter.Chain chain = createMock(Filter.Chain.class);
        chain.doFilter(exchange);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);
    }

    @Test
    public void handleOptionsConfigCorsPreflight() throws IOException {
        // A real CORS preflight: OPTIONS + Origin + Access-Control-Request-Method.
        // These are exactly the three conditions CorsFilter.isCORSPreFlightRequest() checks.
        // The filter must pass it through so JolokiaHttpHandler can write Access-Control-Allow-* headers.
        Filter f = new ConfigEndpointFilter("/jolokia");

        Headers requestHeaders = new Headers();
        requestHeaders.set("Origin", "http://example.com");
        requestHeaders.set("Access-Control-Request-Method", "GET");

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/jolokia/config"));
        expect(exchange.getRequestMethod()).andReturn("OPTIONS").anyTimes();
        expect(exchange.getRequestHeaders()).andReturn(requestHeaders);

        Filter.Chain chain = createMock(Filter.Chain.class);
        chain.doFilter(exchange);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);
    }

    @Test
    public void rejectBareOptionsOnConfigEndpoint() throws IOException {
        // A plain OPTIONS (no Origin / no Access-Control-Request-Method) is not a CORS preflight.
        // It must be rejected with 405, same as POST/PUT/DELETE.
        Filter f = new ConfigEndpointFilter("/jolokia");

        Headers requestHeaders = new Headers();   // no Origin, no Access-Control-Request-Method

        Headers responseHeaders = new Headers();
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/jolokia/config"));
        expect(exchange.getRequestMethod()).andReturn("OPTIONS").anyTimes();
        expect(exchange.getRequestHeaders()).andReturn(requestHeaders);
        expect(exchange.getResponseHeaders()).andReturn(responseHeaders).anyTimes();
        exchange.sendResponseHeaders(eq(405), anyLong());
        expect(exchange.getResponseBody()).andReturn(responseBody).anyTimes();

        Filter.Chain chain = createMock(Filter.Chain.class);   // no expectation → call would fail

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);

        assertEquals(responseHeaders.getFirst("Allow"), "GET",
                "405 on bare OPTIONS must still advertise the only allowed method");
    }

    @Test
    public void handleGetConfigWithNonDefaultContext() throws IOException {
        // Confirms the filter works for a custom context path, not just the default /jolokia
        Filter f = new ConfigEndpointFilter("/myapp");

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/myapp/config"));
        expect(exchange.getRequestMethod()).andReturn("GET");

        Filter.Chain chain = createMock(Filter.Chain.class);
        chain.doFilter(exchange);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);
    }

    @Test
    public void handleGetConfigWithNonComplexDefaultContext() throws IOException {
        // Confirms the filter works for a custom context path, not just the default /jolokia
        Filter f = new ConfigEndpointFilter("/myapp/path");

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/myapp/path/config"));
        expect(exchange.getRequestMethod()).andReturn("GET");

        Filter.Chain chain = createMock(Filter.Chain.class);
        chain.doFilter(exchange);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);
    }

    // -------------------------------------------------------------------------
    // Rejected with 405: exact path but wrong HTTP method
    // chain.doFilter() must NOT be called; response headers must contain Allow: GET
    // -------------------------------------------------------------------------

    @Test
    public void rejectPostOnConfigEndpoint() throws IOException {
        assertMethodNotAllowed("POST");
    }

    @Test
    public void rejectPutOnConfigEndpoint() throws IOException {
        assertMethodNotAllowed("PUT");
    }

    @Test
    public void rejectDeleteOnConfigEndpoint() throws IOException {
        assertMethodNotAllowed("DELETE");
    }

    private void assertMethodNotAllowed(String method) throws IOException {
        Filter f = new ConfigEndpointFilter("/jolokia");

        Headers responseHeaders = new Headers();

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create("/jolokia/config"));
        expect(exchange.getRequestMethod()).andReturn(method).anyTimes();
        expect(exchange.getRequestHeaders()).andReturn(new Headers());
        expect(exchange.getResponseHeaders()).andReturn(responseHeaders).anyTimes();
        exchange.sendResponseHeaders(eq(405), anyLong());

        // chain must NOT be called - no expectation set means call would fail
        Filter.Chain chain = createMock(Filter.Chain.class);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);

        assertEquals(responseHeaders.getFirst("Allow"), "GET",
                "405 response must advertise the only allowed method via Allow header");
    }

    @Test
    public void rejectGetOnStringPrefixFalseMatch() throws IOException {
        // JDK HTTP Server routes /jolokia/configEvil to the /jolokia/config context
        // because it does string-prefix not path-prefix matching (fixed only in JDK 27).
        // The filter must reject this with 404.
        assertNotFoundPath("/jolokia/configEvil", "GET");
    }

    @Test
    public void rejectPostOnStringPrefixFalseMatch() throws IOException {
        assertNotFoundPath("/jolokia/configEvil", "POST");
    }

    @Test
    public void rejectGetOnConfigurationPath() throws IOException {
        // /jolokia/configuration also matches the /jolokia/config string prefix
        assertNotFoundPath("/jolokia/configuration", "GET");
    }

    @Test
    public void rejectGetOnConfigSubPath() throws IOException {
        // /jolokia/config/extra would be a sub-path - not the exact endpoint
        assertNotFoundPath("/jolokia/config/extra", "GET");
    }

    private void assertNotFoundPath(String path, String method) throws IOException {
        Filter f = new ConfigEndpointFilter("/jolokia");

        Headers responseHeaders = new Headers();
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        HttpExchange exchange = createMock(HttpExchange.class);
        expect(exchange.getRequestURI()).andReturn(URI.create(path));
        expect(exchange.getRequestMethod()).andStubReturn(method);
        expect(exchange.getRequestHeaders()).andReturn(new Headers()).anyTimes();
        expect(exchange.getResponseHeaders()).andReturn(responseHeaders).anyTimes();
        exchange.sendResponseHeaders(eq(404), anyLong());
        expect(exchange.getResponseBody()).andReturn(responseBody).anyTimes();

        Filter.Chain chain = createMock(Filter.Chain.class);

        replay(exchange, chain);
        f.doFilter(exchange, chain);
        verify(exchange, chain);
    }

}
