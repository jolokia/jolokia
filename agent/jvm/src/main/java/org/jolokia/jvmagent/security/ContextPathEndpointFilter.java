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

/**
 * A filter that implements proper "context path" handling which JDK HTTP Server does only since JDK 27.
 * See <a href="https://bugs.openjdk.org/browse/JDK-8377237">JDK-8377237</a>.
 */
public class ContextPathEndpointFilter extends Filter {

    private final String contextPath;

    public ContextPathEndpointFilter(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public String description() {
        return "Context path enforcement filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI().getPath();

        // URI must be equal to context path or starts with context path and slash
        if (uri.equals(contextPath) || (uri.startsWith(contextPath + "/"))) {
            // paths like "/jolokia/" or "/jolokia/anything" will be validated later
            chain.doFilter(exchange);
            return;
        }

        exchange.getResponseHeaders().add("Connection", "close");
        exchange.sendResponseHeaders(404, -1);
    }

}
