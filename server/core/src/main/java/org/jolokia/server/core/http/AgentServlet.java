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
package org.jolokia.server.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.security.auth.Subject;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jolokia.json.JSONStructure;
import org.jolokia.server.core.config.ConfigExtractor;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.restrictor.RestrictorFactory;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.AgentDetails;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.service.api.SecurityDetails;
import org.jolokia.server.core.service.impl.ClasspathServiceCreator;
import org.jolokia.core.util.ClassUtil;
import org.jolokia.server.core.util.IoUtil;
import org.jolokia.server.core.util.MimeTypeUtil;
import org.jolokia.server.core.util.NetworkUtil;

/**
 * Agent servlet which connects to a local JMX MBeanServer for
 * JMX operations.
 *
 * <p>
 * It uses a REST based approach which translates a GET Url into a
 * request. See the <a href="http://www.jolokia.org/reference/index.html">reference documentation</a>
 * for a detailed description of this servlet's features.
 * </p>
 *
 * @author roland@jolokia.org
 * @since Apr 18, 2009
 */
public class AgentServlet extends HttpServlet {

    // no one should serialize servlets nowadays...
//    private static final long serialVersionUID = 42L;

    /**
     * A key for a {@link jakarta.servlet.ServletContext#getAttribute} to check for pre configured
     * <em>authentication method</em> to
     * {@link org.jolokia.server.core.service.api.SecurityDetails#registerAuthenticationMethod register} on
     * behalf of the web application that uses this servlet.
     */
    public static final String EXTERNAL_BASIC_AUTH_REALM = ".jolokia.basicAuth.realm";

    // POST- and GET- HttpRequestHandler
    private ServletRequestHandler httpGetHandler, httpPostHandler;

    // Request handler for parsing request parameters and building up a response
    private HttpRequestHandler requestHandler;

    // Restrictor to use as given in the constructor
    private final Restrictor initRestrictor;

    // named Restrictors to choose from in environments, where restrictors can be configured in
    // some registry (Spring DI, CDI, OSGI maybe, ...)
    private final Map<String, Restrictor> initRestrictors = new LinkedHashMap<>();

    // If discovery multicast is enabled and URL should be initialized by request
    private boolean initAgentUrlFromRequest = false;

    // context for this servlet
    private JolokiaContext jolokiaContext;

    // Mime type used for returning the answer
    private String configMimeType;

    // Service manager for creating/destroying the Jolokia context
    private JolokiaServiceManager serviceManager;

    // whether to allow reverse DNS lookup for checking the remote host
    private boolean allowDnsReverseLookup;

    /**
     * No argument constructor, used e.g. by a servlet
     * descriptor when creating the servlet out of web.xml
     */
    public AgentServlet() {
        this(null);
    }

    /**
     * Constructor taking a restrictor to use
     *
     * @param pRestrictor restrictor to use or <code>null</code> if the restrictor
     *        should be created in the default way by doing a lookup and use the standard
     *        restrictors.
     */
    public AgentServlet(Restrictor pRestrictor) {
        initRestrictor = pRestrictor;
    }

    /**
     * Initialize the backend systems by creating a {@link JolokiaServiceManager}
     *
     * A subclass can tune this step by overriding
     * {@link #createLogHandler}, {@link #createRestrictor} and {@link #createWebConfig}
     *
     * @param pServletConfig servlet configuration
     */
    @Override
    public void init(ServletConfig pServletConfig) throws ServletException {
        super.init(pServletConfig);

        // Create configuration, log handler and restrictor early in the lifecycle
        // and explicitly
        Configuration config = createWebConfig();
        LogHandler logHandler = createLogHandler(pServletConfig, config);
        Restrictor restrictor = createRestrictor(config, logHandler);

        Object realmAttribute = pServletConfig.getServletContext().getAttribute(EXTERNAL_BASIC_AUTH_REALM);
        if (realmAttribute instanceof String realm) {
            // this is how Hawtio can tell Jolokia that it's accessible using Basic authentication
            // not perfect and we may change it later
            config.getSecurityDetails().registerAuthenticationMethod(SecurityDetails.AuthMethod.BASIC, realm);
        }

        // Create the service manager and initialize
        serviceManager =
            JolokiaServiceManagerFactory.createJolokiaServiceManager(config, logHandler, restrictor, getServerDetectorLookup());
        initServices(pServletConfig, serviceManager);

        // Start it up, all static (non-OSGi) services should be available/discovered already ....
        jolokiaContext = serviceManager.start();
        requestHandler = new HttpRequestHandler(jolokiaContext);
        allowDnsReverseLookup = Boolean.parseBoolean(config.getConfig(ConfigKey.ALLOW_DNS_REVERSE_LOOKUP));

        // Different HTTP request handlers
        httpGetHandler = newGetHttpRequestHandler();
        httpPostHandler = newPostHttpRequestHandler();

        initAgentUrl();

        configMimeType = config.getConfig(ConfigKey.MIME_TYPE);
    }

    @Override
    public void destroy() {
        serviceManager.stop();
    }

    public Map<String, Restrictor> getInitRestrictors() {
        return initRestrictors;
    }

    /**
     * Initialize services and register service factories
     * @param pServletConfig servlet configuration
     * @param pServiceManager service manager to which to add services
     */
    protected void initServices(ServletConfig pServletConfig, JolokiaServiceManager pServiceManager) {
        // agent servlet simply uses own (webapp's) classloader
        pServiceManager.addServices(new ClasspathServiceCreator(AgentServlet.class.getClassLoader(), "services"));
    }


    /**
     * Hook for allowing a custom detector lookup
     *
     * @return detector lookup class to use in addition to the standard classpath scanning or null if this is not
     * needed
     */
    protected ServerDetectorLookup getServerDetectorLookup() {
        return null;
    }

    /**
     * Examines servlet config and servlet context for configuration parameters.
     * Configuration from the servlet context overrides servlet parameters defined in web.xml.
     * This method can be subclassed in order to provide an own mechanism for
     * providing a configuration.
     *
     * @return generated configuration
     */
    protected Configuration createWebConfig() throws ServletException {
        StaticConfiguration config = new StaticConfiguration(
                Collections.singletonMap(ConfigKey.AGENT_ID.getKeyValue(),
                                         NetworkUtil.getAgentId(this.hashCode(), "servlet")));
        // from ServletConfig - for Jolokia servlet (<servlet>/<init-param> in web.xml)
        config.update(new ServletConfigFacade(getServletConfig()));
        // from ServletContext - for entire web application (<context-param> in web.xml)
        config.update(new ServletContextFacade(getServletContext()));

        String basicRealm = config.getConfig(ConfigKey.BASIC_AUTHENTICATION_REALM);
        if (basicRealm != null && !basicRealm.isEmpty()) {
            config.addSupportedAuthentication(SecurityDetails.AuthMethod.BASIC, basicRealm);
        }
        String mtlsEnabled = config.getConfig(ConfigKey.MTLS_AUTHENTICATION_ENABLED);
        if (mtlsEnabled != null && !(ConfigKey.enabledValues.contains(mtlsEnabled) || ConfigKey.disabledValues.contains(mtlsEnabled))) {
            throw new ServletException("Invalid value of " + ConfigKey.MTLS_AUTHENTICATION_ENABLED.getKeyValue() + " parameter");
        }
        if (Boolean.parseBoolean(mtlsEnabled)) {
            config.addSupportedAuthentication(SecurityDetails.AuthMethod.MTLS, null);
        }

        return config;
    }

    /**
     * Create a log handler using this servlet's logging facility for logging. This method can be overridden
     * to provide a custom log handler.
     *
     *
     * @param pServletConfig servlet config
     * @param pConfig jolokia config
     * @return a default log handler
     */
    protected LogHandler createLogHandler(ServletConfig pServletConfig, Configuration pConfig) {
        String logHandlerClass = pConfig.getConfig(ConfigKey.LOGHANDLER_CLASS);
        String logHandlerName = pConfig.getConfig(ConfigKey.LOGHANDLER_NAME);
        boolean debug = Boolean.parseBoolean(pConfig.getConfig(ConfigKey.DEBUG));
        return logHandlerClass != null ?
                ClassUtil.newLogHandlerInstance(logHandlerClass, logHandlerName, debug) :
                new ServletLogHandler(debug);
    }
    /**
     * Create a restrictor to use. By default, this method returns the restrictor given in the
     * constructor or does a lookup for a policy rule,
     * but this can be overridden in order to fine tune the creation.
     *
     * @return the restrictor to use
     */
    protected Restrictor createRestrictor(Configuration pConfig, LogHandler pLogHandler) {
        if (initRestrictor != null) {
            return initRestrictor;
        }
        if (!initRestrictors.isEmpty()) {
            Map.Entry<String, Restrictor> e = initRestrictors.entrySet().iterator().next();
            if (initRestrictors.size() > 1) {
                pLogHandler.info("Multiple restrictors configured, will use \"" + e.getKey() + "\".");
            } else {
                pLogHandler.info("Using registered restrictor \"" + e.getKey() + "\".");
            }
            return e.getValue();
        }
        // fallback to static (traditional) creation of the restrictor from properties configuration
        return RestrictorFactory.createRestrictor(pConfig, pLogHandler);
    }

    // ==============================================================================================

    private void initAgentUrl() {
        String url = jolokiaContext.getConfig(ConfigKey.DISCOVERY_AGENT_URL);
        if (url == null) {
            initAgentUrlFromRequest = true;
        } else {
            initAgentUrlFromRequest = false;
            jolokiaContext.getAgentDetails().updateAgentParameters(url, null);
        }
    }

    // A loghandler using a servlets log facilities
    private final class ServletLogHandler implements LogHandler {

        private final boolean debug;

        private ServletLogHandler(boolean pDebug) {
            debug = pDebug;
        }

        /** {@inheritDoc} */
        public void debug(String message) {

            log(message);
        }

        /** {@inheritDoc} */
        public void info(String message) {
            log(message);
        }

        /** {@inheritDoc} */
        public void error(String message, Throwable t) {
            log(message,t);
        }

        /** {@inheritDoc} */
        public boolean isDebug() {
            return debug;
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(httpGetHandler, req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(httpPostHandler, req, resp);
    }

    /**
     * OPTION requests are treated as CORS preflight requests
     *
     * @param req the original request
     * @param resp the response the answer are written to
     * */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        Map<String,String> responseHeaders =
                requestHandler.handleCorsPreflightRequest(
                        extractOriginOrReferer(req),
                        req.getHeader("Access-Control-Request-Headers"));
        for (Map.Entry<String,String> entry : responseHeaders.entrySet()) {
            resp.setHeader(entry.getKey(),entry.getValue());
        }
    }

    /**
     * Handle an incoming GET or POST request in a unified way. This is the Servlet API equivalent of
     * {@code org.jolokia.jvmagent.handler.JolokiaHttpHandler#handle(com.sun.net.httpserver.HttpExchange)} from
     * JVM Agent which uses {@code com.sun.net.httpserver}
     *
     * @param pReqHandler
     * @param pReq
     * @param pResp
     * @throws IOException
     */
    private void handle(ServletRequestHandler pReqHandler, HttpServletRequest pReq, HttpServletResponse pResp) throws IOException {
        Subject subject = (Subject) pReq.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE);
        if (subject != null) {
            doHandleAs(subject, pReqHandler, pReq, pResp);
        } else {
            doHandle(pReqHandler, pReq, pResp);
        }
    }

    /**
     * Handle the incoming request within a {@link java.security.PrivilegedAction} when a {@link Subject} was found.
     *
     * @param pSubject
     * @param pReqHandler
     * @param pReq
     * @param pResp
     * @return
     * @throws IOException
     */
    private void doHandleAs(Subject pSubject, final ServletRequestHandler pReqHandler, final HttpServletRequest pReq, final HttpServletResponse pResp)
            throws IOException {
        try {
            Subject.doAs(pSubject, (PrivilegedExceptionAction<Void>) () -> {
                doHandle(pReqHandler,pReq, pResp);
                return null;
            });
        } catch (PrivilegedActionException | SecurityException e) {
            // PrivilegedActionException happens _only_ when the action has thrown an checked exception.
            // But we handle all java.lang.Throwables in the doHandle() method anyway.
            // SecurityException happens _only_ under a SecurityManager and it's being removed anyway.
            sendInternalServerError(pResp, new Exception(e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
        }
    }

    /**
     * Handle the incoming request. All exceptions during request processing are caught and turned into a Jolokia
     * JSON error. The only exception thrown is when the handled exception (turned into JSON) can't be sent back.
     *
     * @param pReqHandler
     * @param pReq
     * @param pResp
     * @throws IOException thrown only if there's an issue sending the response.
     */
    private void doHandle(ServletRequestHandler pReqHandler, HttpServletRequest pReq, HttpServletResponse pResp) throws IOException {
        JSONStructure json;

        try {
            // Set back channel - for notification handling
            prepareBackChannel(pReq);

            // Check access policy
            String remoteHost = allowDnsReverseLookup ? pReq.getRemoteHost() : null;
            String scheme = pReq.getScheme();
            requestHandler.checkAccess(scheme, remoteHost, pReq.getRemoteAddr(), extractOriginOrReferer(pReq));

            // If a callback is given, check this is a valid javascript function name
            validateCallbackIfGiven(pReq);

            // Remember the agent URL upon the first request. Needed for discovery
            updateAgentDetailsIfNeeded(pReq);

            // Dispatch for the proper HTTP request method - the returned JSON object may be a proper Jolokia JSON
            // response or error Jolokia JSON response
            json = pReqHandler.handleRequest(pReq);
        } catch (BadRequestException exp) {
            String response = "400 (Bad Request)\n";
            if (exp.getMessage() != null) {
                response += "\n" + exp.getMessage() + "\n";
            }
            pResp.addHeader("Content-Type", "text/plain; charset=utf-8");
            pResp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            OutputStream os = pResp.getOutputStream();
            os.write(response.getBytes());
            os.close();
            return;
        } catch (EmptyResponseException exp) {
            // Don't close the connection, this is used for `Content-Type: text/event-stream` for notifications
            // which will be sent using org.jolokia.service.notif.sse.SseHeartBeat
            return;
        } catch (Throwable exp) {
            // handle exception not handled by org.jolokia.server.core.http.HttpRequestHandler()
            // so for example should not be JMX exceptions
            json = requestHandler.handleThrowable(exp);
        } finally {
            releaseBackChannel();
            setCorsHeader(pReq, pResp);
        }

        if (json == null) {
            sendInternalServerError(pResp, new Exception("Internal Server Error (no JSON response)"));
        } else {
            sendResponse(pResp, pReq, json);
        }
    }

    private void prepareBackChannel(HttpServletRequest pReq) {
        BackChannelHolder.set(new ServletBackChannel(pReq));
    }

    private void releaseBackChannel() {
        BackChannelHolder.remove();
    }

    private String extractOriginOrReferer(HttpServletRequest pReq) {
        String origin = pReq.getHeader("Origin");
        if (origin == null) {
            origin = pReq.getHeader("Referer");
        }
        return origin != null ? origin.replaceAll("[\\n\\r]*","") : null;
    }

    // Update the agent URL in the agent details if not already done
    private void updateAgentDetailsIfNeeded(HttpServletRequest pReq) {
        // Lookup the Agent URL if needed
        if (initAgentUrlFromRequest) {
            updateAgentUrl(NetworkUtil.sanitizeLocalUrl(pReq.getRequestURL().toString()), extractServletPath(pReq),pReq.getAuthType() != null);
            initAgentUrlFromRequest = false;
        }
    }
    // Update the URL in the AgentDetails
    private void updateAgentUrl(String pRequestUrl, String pServletPath, boolean pIsAuthenticated) {
        AgentDetails details = jolokiaContext.getAgentDetails();
        if (details.isInitRequired()) {
           synchronized(details) {
               if (details.isInitRequired()) {
                   if (details.isUrlMissing()) {
                       String url = getBaseUrl(NetworkUtil.sanitizeLocalUrl(pRequestUrl), pServletPath);
                       details.setUrl(url);
                   }
                   if (details.isSecuredMissing()) {
                       details.setSecured(pIsAuthenticated);
                   }
                   details.seal();
               }
           }
        }
        details.seal();
    }

    // Strip off everything unneeded
    private String getBaseUrl(String pUrl, String pServletPath) {
        String sUrl;
        try {
            URL url = new URL(pUrl);
            String host = getIpIfPossible(url.getHost());
            sUrl = new URL(url.getProtocol(),host,url.getPort(),pServletPath).toExternalForm();
        } catch (MalformedURLException exp) {
            sUrl = plainReplacement(pUrl, pServletPath);
        }
        return sUrl;
    }

    // Check for an IP, since this seems to be safer to return then a plain name
    private String getIpIfPossible(String pHost) {
        try {
            InetAddress address = InetAddress.getByName(pHost);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return pHost;
        }
    }

    // Fallback used if URL creation didn't work
    private String plainReplacement(String pUrl, String pServletPath) {
        int idx = pUrl.lastIndexOf(pServletPath);
        String url;
        if (idx != -1) {
            url = pUrl.substring(0,idx) + pServletPath;
        } else {
            url = pUrl;
        }
        return url;
    }

    // Check the proper servlet path
    private String extractServletPath(HttpServletRequest pReq) {
        // for root mapping, the context path is ""
        // for non-root, it's "/context"
        // servlet path is always within context and when servlet is mapped with "/*", the servlet path is "" (empty)
        String uri = pReq.getRequestURI();
        int len = pReq.getContextPath().length();
        String servletPath = pReq.getServletPath();
        if (servletPath != null) {
            len += servletPath.length();
        }
        return uri.substring(0, len);
    }

    // Set an appropriate CORS header if requested and if allowed
    private void setCorsHeader(HttpServletRequest pReq, HttpServletResponse pResp) {
        String origin = requestHandler.extractCorsOrigin(pReq.getHeader("Origin"));
        if (origin != null) {
            pResp.setHeader("Access-Control-Allow-Origin",origin);
            pResp.setHeader("Access-Control-Allow-Credentials","true");
        }
    }

    /**
     * A little abstraction of a handler that turns incoming {@link HttpServletRequest} into a {@link JSONStructure}.
     * Sending to an outgoing {@link HttpServletResponse} is done by the caller.
     */
    private interface ServletRequestHandler {
        /**
         * Handle a request and return the answer as a JSON structure. There's no interaction with the Servlet
         * response
         *
         * @param pReq incoming {@link HttpServletRequest}
         * @return the JSON representation for the answer
         * @throws IOException if reading input stream fails - so it doesn't have to be sender's fault
         * @throws BadRequestException if there's a parsing error or parameter processing error (always sender's fault)
         * @throws EmptyResponseException if the connection should not be closed (only for notifications)
         */
        JSONStructure handleRequest(HttpServletRequest pReq) throws IOException, BadRequestException, EmptyResponseException;
    }

    /**
     * Factory method for GET request handler using Servlet API
     * @return
     */
    private ServletRequestHandler newGetHttpRequestHandler() {
        return new ServletRequestHandler() {
            @Override
            public JSONStructure handleRequest(HttpServletRequest pReq) throws BadRequestException, EmptyResponseException {
                return requestHandler.handleGetRequest(pReq.getRequestURI(), pReq.getPathInfo(), getParameterMap(pReq));
            }
        };
    }

    /**
     * Factory method for POST request handler using Servlet API
     * @return
     */
    private ServletRequestHandler newPostHttpRequestHandler() {
        return new ServletRequestHandler() {
            @Override
            public JSONStructure handleRequest(HttpServletRequest pReq) throws IOException, BadRequestException, EmptyResponseException {
                String encoding = pReq.getCharacterEncoding();
                InputStream is = pReq.getInputStream();
                return requestHandler.handlePostRequest(pReq.getRequestURI(), is, encoding, getParameterMap(pReq));
            }
        };
    }

    // =======================================================================

    // Get parameter map either directly from a Servlet 2.4 compliant implementation
    // or by looking it up explicitly (thanks to codewax for the patch)
    private Map<String, String[]> getParameterMap(HttpServletRequest pReq){
        try {
            // Servlet 2.4 API
            return pReq.getParameterMap();
        } catch (UnsupportedOperationException exp) {
            // Thrown by 'pseudo' 2.4 Servlet API implementations which fake a 2.4 API
            // As a service for the parameter map is build up explicitly
            Map<String, String[]> ret = new HashMap<>();
            Enumeration<String> params = pReq.getParameterNames();
            while (params.hasMoreElements()) {
                String param = params.nextElement();
                ret.put(param, pReq.getParameterValues(param));
            }
            return ret;
        }
    }

    /**
     * Called when we can't produce a Jolokia JSON response or error.
     *
     * @param res
     * @param exception
     * @throws IOException
     */
    private void sendInternalServerError(HttpServletResponse res, Exception exception) throws IOException {
        String response = "500 (Internal Server Error)\n";
        if (exception != null && exception.getMessage() != null) {
            response += "\n" + exception.getMessage() + "\n";
        }
        setContentType(res, "text/plain");
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        setNoCacheHeaders(res);
        OutputStream os = res.getOutputStream();
        os.write(response.getBytes());
        os.close();
    }

    private void sendResponse(HttpServletResponse pResp, HttpServletRequest pReq, JSONStructure pJson) throws IOException {
        String callback = pReq.getParameter(ConfigKey.CALLBACK.getKeyValue());

        setContentType(pResp,
                       MimeTypeUtil.getResponseMimeType(
                           pReq.getParameter(ConfigKey.MIME_TYPE.getKeyValue()),
                           configMimeType, callback));
        pResp.setStatus(HttpServletResponse.SC_OK);
        setNoCacheHeaders(pResp);
        if (pJson == null) {
            pResp.setContentLength(-1);
        } else {
            sendStreamingResponse(pResp, callback, pJson);
        }
    }

    private void validateCallbackIfGiven(HttpServletRequest pReq) throws BadRequestException {
        String callback = pReq.getParameter(ConfigKey.CALLBACK.getKeyValue());
        if (callback != null && !MimeTypeUtil.isValidCallback(callback)) {
            throw new BadRequestException("Invalid callback name given, which must be a valid javascript function name");
        }
    }
    private void sendStreamingResponse(HttpServletResponse pResp, String pCallback, JSONStructure pJson) throws IOException {
        Writer writer = new OutputStreamWriter(pResp.getOutputStream(), StandardCharsets.UTF_8);
        IoUtil.streamResponseAndClose(writer, pJson, pCallback);
    }

    private void setNoCacheHeaders(HttpServletResponse pResp) {
        pResp.setHeader("Cache-Control", "no-cache");
        // Check for a date header and set it accordingly to the recommendations of
        // RFC-2616 (http://tools.ietf.org/html/rfc2616#section-14.21)
        //
        //   "To mark a response as "already expired," an origin server sends an
        //    Expires date that is equal to the Date header value. (See the rules
        //  for expiration calculations in section 13.2.4.)"
        //
        // See also #71

        long now = System.currentTimeMillis();
        pResp.setDateHeader("Date",now);
        // 1h  in the past since it seems, that some servlet set the date header on their
        // own so that it cannot be guaranteed that these headers are really equals.
        // It happened on Tomcat that Date: was finally set *before* Expires: in the final
        // answers sometimes which seems to be an implementation peculiarity from Tomcat
        pResp.setDateHeader("Expires",now - 3600000);
    }

    private void setContentType(HttpServletResponse pResp, String pContentType) {
        boolean encodingDone = false;
        try {
            pResp.setCharacterEncoding("utf-8");
            pResp.setContentType(pContentType);
            encodingDone = true;
        }
        catch (NoSuchMethodError error) { /* Servlet 2.3 */ }
        catch (UnsupportedOperationException error) { /* Equinox HTTP Service */ }
        if (!encodingDone) {
            // For a Servlet 2.3 container or an Equinox HTTP Service, set the charset by hand
            pResp.setContentType(pContentType + "; charset=utf-8");
        }
    }

    // =======================================================================================
    // Helper classes for extracting configuration from servlet classes


    // Implementation for the ServletConfig
    private static final class ServletConfigFacade implements ConfigExtractor {
        private final ServletConfig config;

        private ServletConfigFacade(ServletConfig pConfig) {
            config = pConfig;
        }

        /** {@inheritDoc} */
        public Enumeration<String> getNames() {
            return config.getInitParameterNames();
        }

        /** {@inheritDoc} */
        public String getParameter(String pName) {
            return config.getInitParameter(pName);
        }
    }

    // Implementation for ServletContextFacade
    private static final class ServletContextFacade implements ConfigExtractor {
        private final ServletContext servletContext;

        private ServletContextFacade(ServletContext pServletContext) {
            servletContext = pServletContext;
        }

        /** {@inheritDoc} */
        public Enumeration<String> getNames() {
            return servletContext.getInitParameterNames();
        }

        /** {@inheritDoc} */
        public String getParameter(String pName) {
            return servletContext.getInitParameter(pName);
        }
    }

}
