package org.jolokia.jvmagent.handler;/*
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
import java.util.concurrent.Executor;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.server.core.http.BackChannel;

/**
 * @author roland
 * @since 14/12/15
 */
public class HttpExchangeBackChannel implements BackChannel, Runnable {

    private HttpExchange exchange;
    private final Executor executor;

    private boolean closed = true;

    public HttpExchangeBackChannel(HttpExchange pExchange,Executor pExecutor) {
        this.exchange = pExchange;
        this.executor = pExecutor;
    }

    @Override
    public synchronized void open(Map<String, ?> pParams) throws IOException {
        if (exchange == null) {
            throw new IllegalStateException("Channel has been already used and can't be reused. " +
                                            "You need to create a new channel");
        }
        setResponseHeaders(pParams);
        closed = false;
        executor.execute(this);
    }

    // Wait forever until closed
    public void run() {
        synchronized (this) {
            while (!closed) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    // might happen when close is called ...
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            this.notifyAll();

            // Close exchange which makes it invalid
            exchange.close();
            exchange = null;
        }
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized OutputStream getOutputStream() throws IOException {
        if (!closed) {
            return exchange.getResponseBody();
        } else {
            throw new IOException("Channel is already closed");
        }
    }

    // =====================================================

    private void setResponseHeaders(Map<String, ?> pParams) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        if (pParams.containsKey(BackChannel.CONTENT_TYPE)) {
            String ct = (String) pParams.get(BackChannel.CONTENT_TYPE);
            if (pParams.containsKey(BackChannel.ENCODING)) {
                ct = ct + ";charset=" + pParams.get(BackChannel.ENCODING);
            }
            headers.add("Content-Type", ct);
        }
        exchange.sendResponseHeaders(200,0);
    }
}
