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

import java.io.IOException;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

public class ConfigEndpointFilter extends Filter {

    private final String contextPath;

    public ConfigEndpointFilter(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public String description() {
        return "/config protection filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI().getPath();

        if (uri.startsWith(contextPath) && !"/".equals(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        if (!"/config".equals(uri)) {
            // whatever the method, we don't handle /configx, /configuration, ...
            exchange.getResponseHeaders().add("Connection", "close");
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        if ("GET".equals(method) || CorsFilter.isCORSPreFlightRequest(exchange)) {
            chain.doFilter(exchange);
            return;
        }

        exchange.getResponseHeaders().add("Allow", "GET");
        exchange.getResponseHeaders().add("Connection", "close");
        exchange.sendResponseHeaders(405, -1);
    }

}
