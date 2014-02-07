package org.jolokia.jvmagent;

/*
 * Copyright 2009-2013 Roland Huss
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
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.RuntimeMBeanException;

import com.sun.net.httpserver.*;
import org.jolokia.backend.BackendManager;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.discovery.DiscoveryMulticastResponder;
import org.jolokia.http.HttpRequestHandler;
import org.jolokia.restrictor.*;
import org.jolokia.util.ClassUtil;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONAware;

/**
 * HttpHandler for handling a jolokia request
 *
 * @author roland
 * @since Mar 3, 2010
 */
public class JolokiaHttpHandler implements HttpHandler {

    // Backendmanager for doing request
    private BackendManager backendManager;

    // The HttpRequestHandler
    private HttpRequestHandler requestHandler;

    // Context of this request
    private String context;

    // Content type matching
    private Pattern contentTypePattern = Pattern.compile(".*;\\s*charset=([^;,]+)\\s*.*");

    // Configuration of this handler
    private Configuration    configuration;

    // Formatted for formatting Date response headers
    private final SimpleDateFormat rfc1123Format;

    // Loghandler to use
    private final LogHandler logHandler;

    // Respond for discovery mc requests
    private DiscoveryMulticastResponder discoveryMulticastResponder;

    // Address on which the server binds
    private final InetAddress serverAddress;

    public JolokiaHttpHandler(Configuration pConfig) {
        this(pConfig, null, null);
    }

    /**
     * Create a new HttpHandler for processing HTTP request
     *
     * @param pConfig jolokia specific config tuning the processing behaviour
     */
    public JolokiaHttpHandler(Configuration pConfig, InetAddress pServerAddress) {
        this(pConfig, pServerAddress, null);
    }

    /**
     * Create a new HttpHandler for processing HTTP request
     *
     * @param pConfig jolokia specific config tuning the processing behaviour
     * @param pServerAddress address on which the server binds.
     * @param pLogHandler log-handler the log handler to use for jolokia
     */
    public JolokiaHttpHandler(Configuration pConfig, InetAddress pServerAddress, LogHandler pLogHandler) {
        configuration = pConfig;
        context = pConfig.get(ConfigKey.AGENT_CONTEXT);
        if (!context.endsWith("/")) {
            context += "/";
        }
        serverAddress = pServerAddress;
        rfc1123Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        rfc1123Format.setTimeZone(TimeZone.getTimeZone("GMT"));
        logHandler = pLogHandler != null ? pLogHandler : createLogHandler(pConfig.get(ConfigKey.LOGHANDLER_CLASS),pConfig.get(ConfigKey.DEBUG));
    }

    /**
     * Start the handler
     * @param pLazy whether initialisation should be done lazy.
     */
    public void start(boolean pLazy) {
        Restrictor restrictor = createRestrictor(configuration);
        backendManager = new BackendManager(configuration, logHandler, restrictor, pLazy);
        requestHandler = new HttpRequestHandler(configuration, backendManager, logHandler);
        if (listenForDiscoveryMcRequests(configuration)) {
            try {
                discoveryMulticastResponder = new DiscoveryMulticastResponder(serverAddress,backendManager,restrictor,logHandler);
                discoveryMulticastResponder.start();
            } catch (IOException e) {
                logHandler.error("Cannot start discovery multicast handler: " + e,e);
            }
        }
    }

    private boolean listenForDiscoveryMcRequests(Configuration pConfig) {
        String enable = pConfig.get(ConfigKey.DISCOVERY_ENABLED);
        String url = pConfig.get(ConfigKey.DISCOVERY_AGENT_URL);
        return url != null || enable == null || Boolean.valueOf(enable);
    }

    /**
     * Start the handler and remember connection details which are useful for discovery messages
     *
     * @param pLazy whether initialisation should be done lazy.
     * @param pUrl agent URL
     * @param pConfidence how sure the URL is
     * @param pSecured whether the communication is secured or not
     */
    public void start(boolean pLazy, String pUrl, int pConfidence, boolean pSecured) {
        start(pLazy);

        backendManager.getAgentDetails().updateAgentParameters(pUrl, pConfidence, pSecured);
    }

    /**
     * Stop the handler
     */
    public void stop() {
        discoveryMulticastResponder.stop();
        backendManager.destroy();
        backendManager = null;
        requestHandler = null;
        discoveryMulticastResponder = null;
    }

    /**
     * Handler a request. If the handler is not yet started, an exception is thrown
     *
     * @param pExchange the request/response object
     * @throws IOException if something fails during handling
     * @throws IllegalStateException if the handler has not yet been started
     */
    @Override
    @SuppressWarnings({"PMD.AvoidCatchingThrowable", "PMD.AvoidInstanceofChecksInCatchClause"})
    public void handle(HttpExchange pExchange) throws IOException {
        if (requestHandler == null) {
            throw new IllegalStateException("Handler not yet started");
        }

        JSONAware json = null;
        URI uri = pExchange.getRequestURI();
        ParsedUri parsedUri = new ParsedUri(uri,context);
        try {
            // Check access policy
            InetSocketAddress address = pExchange.getRemoteAddress();
            requestHandler.checkClientIPAccess(address.getHostName(),address.getAddress().getHostAddress());
            String method = pExchange.getRequestMethod();

            // Dispatch for the proper HTTP request method
            if ("GET".equalsIgnoreCase(method)) {
                setHeaders(pExchange);
                json = executeGetRequest(parsedUri);
            } else if ("POST".equalsIgnoreCase(method)) {
                setHeaders(pExchange);
                json = executePostRequest(pExchange, parsedUri);
            } else if ("OPTIONS".equalsIgnoreCase(method)) {
                performCorsPreflightCheck(pExchange);
            } else {
                throw new IllegalArgumentException("HTTP Method " + method + " is not supported.");
            }
            if (backendManager.isDebug()) {
                backendManager.info("Response: " + json);
            }
        } catch (Throwable exp) {
            json = requestHandler.handleThrowable(
                    exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp);
        } finally {
            sendResponse(pExchange,parsedUri,json);
        }
    }

    // ========================================================================


    private Restrictor createRestrictor(Configuration pConfig) {
        String location = pConfig.get(ConfigKey.POLICY_LOCATION);
        try {
            Restrictor ret = RestrictorFactory.lookupPolicyRestrictor(location);
            if (ret != null) {
                logHandler.info("Using access restrictor " + location);
                return ret;
            } else {
                logHandler.info("No access restrictor found, access to all MBean is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            logHandler.error("Error while accessing access restrictor at " + location +
                             ". Denying all access to MBeans for security reasons. Exception: " + e, e);
            return new DenyAllRestrictor();
        }
    }

    private JSONAware executeGetRequest(ParsedUri parsedUri) {
        return requestHandler.handleGetRequest(parsedUri.getUri().toString(),parsedUri.getPathInfo(), parsedUri.getParameterMap());
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
        return requestHandler.handlePostRequest(pUri.toString(),is, encoding, pUri.getParameterMap());
    }

    private void performCorsPreflightCheck(HttpExchange pExchange) {
        Headers requestHeaders = pExchange.getRequestHeaders();
        Map<String,String> respHeaders =
                requestHandler.handleCorsPreflightRequest(requestHeaders.getFirst("Origin"),
                                                          requestHeaders.getFirst("Access-Control-Request-Headers"));
        Headers responseHeaders = pExchange.getResponseHeaders();
        for (Map.Entry<String,String> entry : respHeaders.entrySet()) {
            responseHeaders.set(entry.getKey(), entry.getValue());
        }
    }

    private void setHeaders(HttpExchange pExchange) {
        String origin = requestHandler.extractCorsOrigin(pExchange.getRequestHeaders().getFirst("Origin"));
        Headers headers = pExchange.getResponseHeaders();
        if (origin != null) {
            headers.set("Access-Control-Allow-Origin",origin);
            headers.set("Access-Control-Allow-Credentials","true");
        }

        // Avoid caching at all costs
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma","no-cache");

        // Check for a date header and set it accordingly to the recommendations of
        // RFC-2616. See also {@link AgentServlet#setNoCacheHeaders()}
        // Issue: #71
        Calendar cal = Calendar.getInstance();
        headers.set("Date",rfc1123Format.format(cal.getTime()));
        // 1h  in the past since it seems, that some servlet set the date header on their
        // own so that it cannot be guaranteed that these heades are really equals.
        // It happend on Tomcat that Date: was finally set *before* Expires: in the final
        // answers some times which seems to be an implementation percularity from Tomcat
        cal.add(Calendar.HOUR, -1);
        headers.set("Expires",rfc1123Format.format(cal.getTime()));
    }

    private void sendResponse(HttpExchange pExchange, ParsedUri pParsedUri, JSONAware pJson) throws IOException {
        OutputStream out = null;
        try {
            Headers headers = pExchange.getResponseHeaders();
            if (pJson != null) {
                headers.set("Content-Type", getMimeType(pParsedUri) + "; charset=utf-8");
                String json = pJson.toJSONString();
                String callback = pParsedUri.getParameter(ConfigKey.CALLBACK.getKeyValue());
                String content = callback == null ? json : callback + "(" + json + ");";
                byte[] response = content.getBytes("UTF8");
                pExchange.sendResponseHeaders(200,response.length);
                out = pExchange.getResponseBody();
                out.write(response);
            } else {
                headers.set("Content-Type", "text/plain");
                pExchange.sendResponseHeaders(200,-1);
            }
        } finally {
            if (out != null) {
                // Always close in order to finish the request.
                // Otherwise the thread blocks.
                out.close();
            }
        }
    }

    // Get the proper mime type according to configuration
    private String getMimeType(ParsedUri pParsedUri) {
        if (pParsedUri.getParameter(ConfigKey.CALLBACK.getKeyValue()) != null) {
            return "text/javascript";
        } else {
            String mimeType = pParsedUri.getParameter(ConfigKey.MIME_TYPE.getKeyValue());
            if (mimeType != null) {
                return mimeType;
            }
            mimeType = configuration.get(ConfigKey.MIME_TYPE);
            return mimeType != null ? mimeType : ConfigKey.MIME_TYPE.getDefaultValue();
        }
    }

    // Creat a log handler from either the given class or by creating a default log handler printing
    // out to stderr
    private LogHandler createLogHandler(String pLogHandlerClass, String pDebug) {
        if (pLogHandlerClass != null) {
            return ClassUtil.newInstance(pLogHandlerClass);
        } else {
            final boolean debug = Boolean.valueOf(pDebug);
            return new LogHandler() {
                @Override
                @SuppressWarnings("PMD.SystemPrintln")
                public final void debug(String message) {
                    if (debug) {
                        System.err.println("D> " + message);
                    }
                }

                @Override
                @SuppressWarnings("PMD.SystemPrintln")
                public final void info(String message) {
                    System.err.println("I> " + message);
                }

                @Override
                @SuppressWarnings("PMD.SystemPrintln")
                public final void error(String message, Throwable t) {
                    System.err.println("E> " + message + " (Exception: " + t + ")");
                }
            };
        }
    }
}
