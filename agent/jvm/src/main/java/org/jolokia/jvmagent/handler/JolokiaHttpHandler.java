package org.jolokia.jvmagent.handler;

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
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.RuntimeMBeanException;
import javax.security.auth.Subject;

import com.sun.net.httpserver.*;
import org.jolokia.backend.BackendManager;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.discovery.AgentDetails;
import org.jolokia.discovery.DiscoveryMulticastResponder;
import org.jolokia.http.HttpRequestHandler;
import org.jolokia.jvmagent.ParsedUri;
import org.jolokia.restrictor.*;
import org.jolokia.util.*;
import org.json.simple.JSONAware;
import org.json.simple.JSONStreamAware;

/**
 * HttpHandler for handling a Jolokia request
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
    private Configuration configuration;

    // Loghandler to use
    private final LogHandler logHandler;

    // Respond for discovery mc requests
    private DiscoveryMulticastResponder discoveryMulticastResponder;

    /**
     * Create a new HttpHandler for processing HTTP request
     *
     * @param pConfig jolokia specific config tuning the processing behaviour
     */
    public JolokiaHttpHandler(Configuration pConfig) {
        this(pConfig, null);
    }

    /**
     * Create a new HttpHandler for processing HTTP request
     *
     * @param pConfig     jolokia specific config tuning the processing behaviour
     * @param pLogHandler log-handler the log handler to use for jolokia
     */
    public JolokiaHttpHandler(Configuration pConfig, LogHandler pLogHandler) {
        configuration = pConfig;
        context = pConfig.get(ConfigKey.AGENT_CONTEXT);
        if (!context.endsWith("/")) {
            context += "/";
        }
        logHandler = pLogHandler != null ? pLogHandler : createLogHandler(pConfig.get(ConfigKey.LOGHANDLER_CLASS), pConfig.get(ConfigKey.DEBUG));
    }

    /**
     * Start the handler
     *
     * @param pLazy whether initialisation should be done lazy.
     */
    public void start(boolean pLazy) {
        Restrictor restrictor = createRestrictor();
        backendManager = new BackendManager(configuration, logHandler, restrictor, pLazy);
        requestHandler = new HttpRequestHandler(configuration, backendManager, logHandler);
        if (listenForDiscoveryMcRequests(configuration)) {
            try {
                String multicastGroup = configuration.get(ConfigKey.MULTICAST_GROUP);
                int multicastPort = configuration.getAsInt(ConfigKey.MULTICAST_PORT);
                discoveryMulticastResponder = new DiscoveryMulticastResponder(backendManager, restrictor, multicastGroup, multicastPort, logHandler);
                discoveryMulticastResponder.start();
            } catch (IOException e) {
                logHandler.error("Cannot start discovery multicast handler: " + e, e);
            }
        }
    }

    /**
     * Hook for creating an own restrictor
     *
     * @return return restrictor or null if no restrictor is needed.
     */
    protected Restrictor createRestrictor() {
        return RestrictorFactory.createRestrictor(configuration, logHandler);
    }

    private boolean listenForDiscoveryMcRequests(Configuration pConfig) {
        String enable = pConfig.get(ConfigKey.DISCOVERY_ENABLED);
        String url = pConfig.get(ConfigKey.DISCOVERY_AGENT_URL);
        return url != null || enable == null || Boolean.valueOf(enable);
    }

    /**
     * Start the handler and remember connection details which are useful for discovery messages
     *
     * @param pLazy    whether initialisation should be done lazy.
     * @param pUrl     agent URL
     * @param pSecured whether the communication is secured or not
     */
    public void start(boolean pLazy, String pUrl, boolean pSecured) {
        start(pLazy);

        AgentDetails details = backendManager.getAgentDetails();
        details.setUrl(pUrl);
        details.setSecured(pSecured);
    }

    /**
     * Stop the handler
     */
    public void stop() {
        if (discoveryMulticastResponder != null) {
            discoveryMulticastResponder.stop();
            discoveryMulticastResponder = null;
        }
        backendManager.destroy();
        backendManager = null;
        requestHandler = null;
    }

    /**
     * Handle a request. If the handler is not yet started, an exception is thrown. If running with JAAS
     * security enabled it will run as the given subject.
     *
     * @param pHttpExchange the request/response object
     * @throws IOException if something fails during handling
     * @throws IllegalStateException if the handler has not yet been started
     */
    public void handle(final HttpExchange pHttpExchange) throws IOException {
        try {
            checkAuthentication(pHttpExchange);

            Subject subject = (Subject) pHttpExchange.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE);
            if (subject != null)  {
                doHandleAs(subject, pHttpExchange);
            }  else {
                doHandle(pHttpExchange);
            }
        } catch (SecurityException exp) {
            sendForbidden(pHttpExchange,exp);
        }
    }

    // run as priviledged action
    private void doHandleAs(Subject subject, final HttpExchange pHttpExchange) {
        try {
            Subject.doAs(subject, new PrivilegedExceptionAction<Void>() {
            public Void run() throws IOException {
                doHandle(pHttpExchange);
                return null;
            }
            });
        } catch (PrivilegedActionException e) {
            throw new SecurityException("Security exception: " + e.getCause(),e.getCause());
        }
    }

    /**
     * Protocol based authentication checks called very early and before handling a request.
     * If the check fails a security exception must be thrown
     *
     * The default implementation does nothing and should be overridden for a valid check.
     *
     * @param pHttpExchange exchange to check
     * @throws SecurityException if check fails.
     */
    protected void checkAuthentication(HttpExchange pHttpExchange) throws SecurityException { }

    @SuppressWarnings({"PMD.AvoidCatchingThrowable", "PMD.AvoidInstanceofChecksInCatchClause"})
    public void doHandle(HttpExchange pExchange) throws IOException {
        if (requestHandler == null) {
            throw new IllegalStateException("Handler not yet started");
        }

        JSONAware json = null;
        URI uri = pExchange.getRequestURI();
        ParsedUri parsedUri = new ParsedUri(uri, context);
        try {
            // Check access policy
            InetSocketAddress address = pExchange.getRemoteAddress();
            requestHandler.checkAccess(getHostName(address),
                                       address.getAddress().getHostAddress(),
                                       extractOriginOrReferer(pExchange));
            String method = pExchange.getRequestMethod();

            // If a callback is given, check this is a valid javascript function name
            validateCallbackIfGiven(parsedUri);

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
        } catch (Throwable exp) {
            json = requestHandler.handleThrowable(
                    exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp);
        } finally {
            sendResponse(pExchange, parsedUri, json);
        }
    }


    private void validateCallbackIfGiven(ParsedUri pUri) {
        String callback = pUri.getParameter(ConfigKey.CALLBACK.getKeyValue());
        if (callback != null && !MimeTypeUtil.isValidCallback(callback)) {
            throw new IllegalArgumentException("Invalid callback name given, which must be a valid javascript function name");
        }
    }

    // ========================================================================

    // Used for checking origin or referer is an origin policy is enabled
    private String extractOriginOrReferer(HttpExchange pExchange) {
        Headers headers = pExchange.getRequestHeaders();
        String origin = headers.getFirst("Origin");
        if (origin == null) {
            origin = headers.getFirst("Referer");
        }
        return origin != null ? origin.replaceAll("[\\n\\r]*","") : null;
    }

    // Return hostnmae of given address, but only when reverse DNS lookups are allowed
    private String getHostName(InetSocketAddress address) {
        return configuration.getAsBoolean(ConfigKey.ALLOW_DNS_REVERSE_LOOKUP) ? address.getHostName() : null;
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
                requestHandler.handleCorsPreflightRequest(extractOriginOrReferer(pExchange),
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
        headers.set("Date",formatHeaderDate(cal.getTime()));
        // 1h  in the past since it seems, that some servlet set the date header on their
        // own so that it cannot be guaranteed that these headers are really equals.
        // It happened on Tomcat that "Date:" was finally set *before* "Expires:" in the final
        // answers sometimes which seems to be an implementation peculiarity from Tomcat
        cal.add(Calendar.HOUR, -1);
        headers.set("Expires",formatHeaderDate(cal.getTime()));
    }

    private void sendForbidden(HttpExchange pExchange, SecurityException securityException) throws IOException {
        String response = "403 (Forbidden)\n";
        if (securityException != null && securityException.getMessage() != null) {
            response += "\n" + securityException.getMessage() + "\n";
        }
        pExchange.sendResponseHeaders(403, response.length());
        OutputStream os = pExchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private void sendResponse(HttpExchange pExchange, ParsedUri pParsedUri, JSONAware pJson) throws IOException {
        boolean streaming = configuration.getAsBoolean(ConfigKey.STREAMING);
        if (streaming) {
            JSONStreamAware jsonStream = (JSONStreamAware)pJson;
            sendStreamingResponse(pExchange, pParsedUri, jsonStream);
        } else {
            // Fallback, send as one object
            // TODO: Remove for 2.0
            sendAllJSON(pExchange, pParsedUri, pJson);
        }
    }

    private void sendStreamingResponse(HttpExchange pExchange, ParsedUri pParsedUri, JSONStreamAware pJson) throws IOException {
        Headers headers = pExchange.getResponseHeaders();
        if (pJson != null) {
            headers.set("Content-Type", getMimeType(pParsedUri) + "; charset=utf-8");
            pExchange.sendResponseHeaders(200, 0);
            Writer writer = new OutputStreamWriter(pExchange.getResponseBody(), "UTF-8");

            String callback = pParsedUri.getParameter(ConfigKey.CALLBACK.getKeyValue());
            IoUtil.streamResponseAndClose(writer, pJson, callback != null && MimeTypeUtil.isValidCallback(callback) ? callback : null);
        } else {
            headers.set("Content-Type", "text/plain");
            pExchange.sendResponseHeaders(200,-1);
        }
    }

    private void sendAllJSON(HttpExchange pExchange, ParsedUri pParsedUri, JSONAware pJson) throws IOException {
        OutputStream out = null;
        try {
            Headers headers = pExchange.getResponseHeaders();
            if (pJson != null) {
                headers.set("Content-Type", getMimeType(pParsedUri) + "; charset=utf-8");
                String json = pJson.toJSONString();
                String callback = pParsedUri.getParameter(ConfigKey.CALLBACK.getKeyValue());
                String content = callback != null && MimeTypeUtil.isValidCallback(callback) ? callback + "(" + json + ");" : json;
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
        return MimeTypeUtil.getResponseMimeType(
            pParsedUri.getParameter(ConfigKey.MIME_TYPE.getKeyValue()),
            configuration.get(ConfigKey.MIME_TYPE),
            pParsedUri.getParameter(ConfigKey.CALLBACK.getKeyValue()));
    }

    // Creat a log handler from either the given class or by creating a default log handler printing
    // out to stderr
    private LogHandler createLogHandler(String pLogHandlerClass, String pDebug) {
        if (pLogHandlerClass != null) {
            return ClassUtil.newInstance(pLogHandlerClass);
        } else {
            final boolean debug = Boolean.valueOf(pDebug);
            return new LogHandler.StdoutLogHandler(debug);
        }
    }

    private String formatHeaderDate(Date date) {
        DateFormat rfc1123Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        rfc1123Format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return rfc1123Format.format(date);
    }
}
