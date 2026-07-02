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

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.jolokia.server.core.service.api.Restrictor;

/**
 * A wrapper for actual {@link Authenticator} that can properly handle CORS preflight
 * requests (which should never be subject to authentication).
 */
public class CorsFilter extends Authenticator {

    public static final ThreadLocal<Boolean> corsPreflight = new ThreadLocal<>();

    private final Authenticator delegate;
    private final Restrictor restrictor;
    private final String realm;
    private final boolean useAuth;

    public CorsFilter(Authenticator delegate, String realm, Restrictor restrictor, boolean useAuth) {
        this.delegate = delegate;
        this.realm = realm;
        this.restrictor = restrictor;
        this.useAuth = useAuth;
    }

    @Override
    public Result authenticate(HttpExchange exchange) {
        if (isCORSPreFlightRequest(exchange)) {
            // never authenticate CORS preflight requests
            // and don't send the CORS response headers here - they'll be sent in HttpRequestHandler later
            // because it is used by both JDK HTTP Server handler and AgentServlet
            CorsFilter.corsPreflight.set(true);
        } else {
            if (delegate != null) {
                Result res = delegate.authenticate(exchange);
                if (!(res instanceof Success)) {
                    // send CORS headers, so failed fetch() request can at least analyze the problem
                    String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
                    if (requestOrigin != null && !requestOrigin.trim().isEmpty()
                            && (this.restrictor == null || this.restrictor.isOriginAllowed(requestOrigin, false))) {
                        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", requestOrigin);
                        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", Boolean.toString(useAuth));
                    }
                }
                return res;
            }
        }

        // we have to return something
        return new Success(new HttpPrincipal("anonymous", realm));
    }

    private boolean isCORSPreFlightRequest(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        Headers headers = exchange.getRequestHeaders();
        String requestOrigin = headers.getFirst("Origin");
        String requestMethod = headers.getFirst("Access-Control-Request-Method");

        return "OPTIONS".equals(method)
                && requestOrigin != null && !requestOrigin.isEmpty()
                && ("GET".equals(requestMethod) || "POST".equals(requestMethod));
    }

}
