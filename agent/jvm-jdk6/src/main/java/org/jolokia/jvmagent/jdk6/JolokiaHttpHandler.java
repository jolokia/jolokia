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
import org.jolokia.backend.BackendManager;
import org.jolokia.ConfigKey;
import org.jolokia.http.HttpRequestHandler;
import org.jolokia.LogHandler;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
        URI uri = pExchange.getRequestURI();
        ParsedUri parsedUri = new ParsedUri(uri,context);
        try {
            // Check access policy
            InetSocketAddress address = pExchange.getRemoteAddress();
            requestHandler.checkClientIPAccess(address.getHostName(),address.getAddress().getHostAddress());
            String method = pExchange.getRequestMethod();

            // Dispatch for the proper HTTP request method
            if ("GET".equalsIgnoreCase(method)) {
                json = executeGetRequest(parsedUri);
            } else if ("POST".equalsIgnoreCase(method)) {
                json = executePostRequest(pExchange, parsedUri);
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
            sendResponse(pExchange,parsedUri,code,json.toJSONString());
        }
    }

    private JSONAware executeGetRequest(URI pUri) {
        ParsedUri parsedUri = new ParsedUri(pUri,context);
        return requestHandler.handleGetRequest(pUri.toString(),parsedUri.getPathInfo(), parsedUri.getParameterMap());
    }

    private JSONAware executePostRequest(HttpExchange pExchange, ParsedUri pUri) throws MalformedObjectNameException, IOException {
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
        return requestHandler.handlePostRequest(pUri.toString(),is, encoding);
    }


    private void sendResponse(HttpExchange pExchange, ParsedUri pParsedUri, int pCode, String pJson) throws IOException {
        OutputStream out = null;
        String callback = pParsedUri.getParameter(ConfigKey.CALLBACK.getKeyValue());
        try {
            Headers headers = pExchange.getResponseHeaders();
            headers.set("Content-Type",(callback == null ? "text/plain" : "text/javascript") + "; charset=utf-8");
            String content = callback == null ? pJson : callback + "(" + pJson + ");";
            byte[] response = content.getBytes();
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
