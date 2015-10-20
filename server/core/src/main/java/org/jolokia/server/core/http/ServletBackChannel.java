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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A Backchannel using the async request API as specified in the Servlet 3.0 spec.
 * @author roland
 * @since 19/10/15
 */
public class ServletBackChannel implements BackChannel {

    private final HttpServletRequest request;

    private AsyncContext asyncContext;

    public ServletBackChannel(HttpServletRequest pReq) {
        request = pReq;
    }

    public void open(Map<String, ?> pParams) throws IOException {
        asyncContext = request.startAsync();
        setResponseHeaders(pParams);
        asyncContext.setTimeout(3600 * 1000);
    }

    public void close() {
        try {
            asyncContext.complete();
        } catch (IllegalArgumentException exp) {
            exp.printStackTrace();
        }
    }

    public PrintWriter getWriter() throws IOException {
        return asyncContext.getResponse().getWriter();
    }

    // =====================================================

    private void setResponseHeaders(Map<String, ?> pParams) {
        if (pParams.containsKey(BackChannel.CONTENT_TYPE)) {
            asyncContext.getResponse().setContentType((String) pParams.get(BackChannel.CONTENT_TYPE));
        }
        if (pParams.containsKey(BackChannel.ENCODING)) {
            asyncContext.getResponse().setCharacterEncoding((String) pParams.get(BackChannel.ENCODING));
        }
    }
}
