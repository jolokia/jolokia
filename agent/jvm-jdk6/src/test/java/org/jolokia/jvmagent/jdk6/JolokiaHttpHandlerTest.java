package org.jolokia.jvmagent.jdk6;

/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.easymock.EasyMock;
import org.jolokia.ConfigKey;
import org.testng.annotations.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 24.10.10
 */
public class JolokiaHttpHandlerTest {


    @Test
    public void testCallback() throws IOException, URISyntaxException {
        JolokiaHttpHandler handler = new JolokiaHttpHandler(getConfig());
        HttpExchange exchange = EasyMock.createMock(HttpExchange.class);
        URI uri = new URI("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?callback=data");
        expect(exchange.getRequestURI()).andReturn(uri);
        expect(exchange.getRemoteAddress()).andReturn(new InetSocketAddress(8080));
        expect(exchange.getRequestMethod()).andReturn("GET");
        Headers header = new Headers();
        expect(exchange.getResponseHeaders()).andReturn(header);
        exchange.sendResponseHeaders(anyInt(),anyLong());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        expect(exchange.getResponseBody()).andReturn(out);
        replay(exchange);
        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");

        String result = out.toString("utf-8");
        assertTrue(result.endsWith("});"));
        assertTrue(result.startsWith("data({"));
    }

    public Map<ConfigKey,String> getConfig() {
        Map<ConfigKey,String> map = new HashMap<ConfigKey, String>();
        map.put(ConfigKey.AGENT_CONTEXT,"/jolokia");
        return map;
    }
}
