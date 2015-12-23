package org.jolokia.server.core.http;/*
 * 
 * Copyright 2015 Roland Huss
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
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A back channel using the async request API as specified in the Servlet 3.0 spec.
 * @author roland
 * @since 19/10/15
 */
public class ServletBackChannel implements BackChannel {

    private HttpServletRequest request;
    private AsyncContext asyncContext;

    private boolean closed = true;

    public ServletBackChannel(HttpServletRequest pReq) {
        request = pReq;
    }

    public synchronized void open(Map<String, ?> pParams) throws IOException {
        if (request == null) {
            throw new IllegalStateException("Channel has been already used and can't be reused. " +
                                            "You need to create a new channel");
        }
        asyncContext = request.startAsync();
        setResponseHeaders(pParams);
        asyncContext.setTimeout(3600 * 1000);
        asyncContext.getResponse().flushBuffer();
        closed = false;
    }

    public synchronized void close() {
        if (!closed) {
            asyncContext.complete();
            closed = true;
            request = null;
        }
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public OutputStream getOutputStream() throws IOException {
        if (!closed) {
            return asyncContext.getResponse().getOutputStream();
        } else {
            throw new IOException("Channel already closed");
        }
    }

    // =====================================================

    private void setResponseHeaders(Map<String, ?> pParams) {
        if (pParams.containsKey(BackChannel.CONTENT_TYPE)) {
            asyncContext.getResponse().setContentType((String) pParams.get(BackChannel.CONTENT_TYPE));
        }
        if (pParams.containsKey(BackChannel.ENCODING)) {
            asyncContext.getResponse().setCharacterEncoding((String) pParams.get(BackChannel.ENCODING));
        }
        // Disable HTTP chunking
        ServletResponse response = asyncContext.getResponse();
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader("Connection", "close");
        }

    }
}
