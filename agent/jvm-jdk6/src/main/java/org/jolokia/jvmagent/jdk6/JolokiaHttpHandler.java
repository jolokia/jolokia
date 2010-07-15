package org.jolokia.jvmagent.jdk6;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jolokia.*;
import org.jolokia.backend.BackendManager;
import org.jolokia.ConfigKey;
import org.jolokia.http.HttpRequestHandler;
import org.jolokia.LogHandler;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * HttpHandler for handling a jolokia request
 *
 * @author roland
 * @since Mar 3, 2010
 */
public class JolokiaHttpHandler implements HttpHandler, LogHandler {

    // Backendmanager for doing request
    private BackendManager backendManager;

    // The HttpRequestHandler
    private HttpRequestHandler requestHandler;

    // Context of this request
    private String context;

    // Content type matching
    private Pattern contentTypePattern = Pattern.compile(".*;\\s*charset=([^;,]+)\\s*.*");


    public JolokiaHttpHandler(Map<ConfigKey,String> pConfig) {
        context = pConfig.get(ConfigKey.AGENT_CONTEXT);
        if (!context.endsWith("/")) {
            context += "/";
        }
        backendManager = new BackendManager(pConfig,this);
        requestHandler = new HttpRequestHandler(backendManager,this);
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void handle(HttpExchange pExchange) throws IOException {
        JSONAware json = null;
        int code = 200;
        try {
            // Check access policy
            InetSocketAddress address = pExchange.getRemoteAddress();
            requestHandler.checkClientIPAccess(address.getHostName(),address.getAddress().getHostAddress());
            String method = pExchange.getRequestMethod();

            // Dispatch for the proper HTTP request method
            URI uri = pExchange.getRequestURI();
            if ("GET".equalsIgnoreCase(method)) {
                json = executeGetRequest(uri);
            } else if ("POST".equalsIgnoreCase(method)) {
                json = executePostRequest(pExchange, uri);
            } else {
                throw new IllegalArgumentException("HTTP Method " + method + " is not supported.");
            }
            code = requestHandler.extractResultCode(json);
            if (backendManager.isDebug()) {
                backendManager.info("Response: " + json);
            }
        } catch (Throwable exp) {
            JSONObject error = requestHandler.handleThrowable(exp);
            code = (Integer) error.get("status");
            json = error;
        } finally {
            sendResponse(pExchange,code,json.toJSONString());
        }
    }

    private JSONAware executeGetRequest(URI pUri) {
        ParsedUri parsedUri = new ParsedUri(pUri,context);
        JmxRequest jmxReq =
                JmxRequestFactory.createRequestFromUrl(parsedUri.getPathInfo(),parsedUri.getParameterMap());
        if (backendManager.isDebug() && !"debugInfo".equals(jmxReq.getOperation())) {
            debug("URI: " + pUri);
            debug("Path-Info: " + parsedUri.getPathInfo());
            debug("Request: " + jmxReq.toString());
        }
        return requestHandler.executeRequest(jmxReq);
    }

    private JSONAware executePostRequest(HttpExchange pExchange, URI pUri) throws MalformedObjectNameException, IOException {
        if (backendManager.isDebug()) {
            debug("URI: " + pUri);
        }
        String encoding = null;
        Headers headers = pExchange.getRequestHeaders();
        String cType =  headers.getFirst("Content-Type");
        if (cType != null) {
            Matcher matcher = contentTypePattern.matcher(cType);
            if (matcher.matches()) {
                encoding = matcher.group(1);
            }
        }
        InputStream is = pExchange.getRequestBody();
        return requestHandler.handleRequestInputStream(is, encoding);
    }


    private void sendResponse(HttpExchange pExchange, int pCode, String s) throws IOException {
        OutputStream out = null;
        try {
            Headers headers = pExchange.getResponseHeaders();
            headers.set("Content-Type","text/plain; charset=utf-8");
            byte[] response = s.getBytes();
            pExchange.sendResponseHeaders(pCode,response.length);
            out = pExchange.getResponseBody();
            out.write(response);
        } finally {
            if (out != null) {
                // Always close in order to finish the request.
                // Otherwise the thread blocks.
                out.close();
            }
        }
    }

    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    public void debug(String message) {
        System.err.println("DEBUG: " + message);
    }

    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    public void info(String message) {
        System.err.println("INFO: " + message);
    }

    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    public void error(String message, Throwable t) {
        System.err.println("ERROR: " + message);
    }
}
