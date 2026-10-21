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
package org.jolokia.jvmagent.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jolokia.server.core.util.IoUtil;

public class FavIconHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange pExchange) throws IOException {
        URI uri = pExchange.getRequestURI();
        if (uri.getPath().equals("/favicon.ico")) {
            handleFavicon(pExchange, uri);
            return;
        }

        pExchange.sendResponseHeaders(404, -1);
    }

    private void handleFavicon(HttpExchange pExchange, URI uri) throws IOException {
        Headers headers = pExchange.getResponseHeaders();
        headers.set("Connection", "close");
        if (uri.getPath().equals("/favicon.ico")) {
            // even if we should use IANA's image/vnd.microsoft.icon
            headers.set("Content-Type", "image/x-icon");
            Writer writer = new OutputStreamWriter(pExchange.getResponseBody(), StandardCharsets.UTF_8);

            try (InputStream icon = getClass().getClassLoader().getResourceAsStream("favicon.ico")) {
                if (icon == null) {
                    pExchange.sendResponseHeaders(404, -1);
                    return;
                }
                pExchange.sendResponseHeaders(200, 0);
                IoUtil.copy(pExchange.getResponseBody(), icon);
            } finally {
                pExchange.getResponseBody().close();
            }
        } else {
            pExchange.sendResponseHeaders(404, -1);
            pExchange.getResponseBody().close();
        }
    }

}
