package org.jolokia.server.core.http;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.*;

import javax.management.RuntimeMBeanException;
import javax.security.auth.Subject;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.jolokia.server.core.config.*;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.restrictor.RestrictorFactory;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.impl.ClasspathServiceCreator;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.IoUtil;
import org.jolokia.server.core.util.MimeTypeUtil;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.json.JSONStructure;


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

    private static final long serialVersionUID = 42L;

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
     * Hook for allowing a custome detector lookup
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
    protected Configuration createWebConfig() {
        StaticConfiguration config = new StaticConfiguration(
                Collections.singletonMap(ConfigKey.AGENT_ID.getKeyValue(),
                                         NetworkUtil.getAgentId(this.hashCode(), "servlet")));
        // from ServletConfig - for Jolokia servlet (<servlet>/<init-param> in web.xml)
        config.update(new ServletConfigFacade(getServletConfig()));
        // from ServletContext - for entire web application (<context-param> in web.xml)
        config.update(new ServletContextFacade(getServletContext()));

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
     * constructor or does a lookup for a policy fule,
     * but thie can be overridden in order to fine tune the creation.
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

    /** {@inheritDoc} */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        handle(httpGetHandler, req, resp);
    }

    /** {@inheritDoc} */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
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
                        getOriginOrReferer(req),
                        req.getHeader("Access-Control-Request-Headers"));
        for (Map.Entry<String,String> entry : responseHeaders.entrySet()) {
            resp.setHeader(entry.getKey(),entry.getValue());
        }
    }

    @SuppressWarnings({ "PMD.AvoidCatchingThrowable", "PMD.AvoidInstanceofChecksInCatchClause" })
    private void handle(ServletRequestHandler pReqHandler, HttpServletRequest pReq, HttpServletResponse pResp) throws IOException {
        JSONStructure json = null;

        try {
            // Check access policy
            requestHandler.checkAccess(pReq.getScheme(),
                                       allowDnsReverseLookup ? pReq.getRemoteHost() : null,
                                       pReq.getRemoteAddr(),
                                       getOriginOrReferer(pReq));

            // If a callback is given, check this is a valid javascript function name
            validateCallbackIfGiven(pReq);

            // Remember the agent URL upon the first request. Needed for discovery
            updateAgentDetailsIfNeeded(pReq);

            // Set back channel
            prepareBackChannel(pReq);

            // Dispatch for the proper HTTP request method
            json = handleSecurely(pReqHandler, pReq, pResp);
        } catch (BadRequestException exp) {
            String response = "400 (Bad Request)\n";
            if (exp.getMessage() != null) {
                response += "\n" + exp.getMessage() + "\n";
            }
            pResp.addHeader("Content-Type", "text/plain");
            pResp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            OutputStream os = pResp.getOutputStream();
            os.write(response.getBytes());
            os.close();
            return;
        } catch (EmptyResponseException exp) {
            // Nothing needs to be done
            return;
        } catch (Throwable exp) {
            try {
                json = requestHandler.handleThrowable(
                    exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp);
            } catch (Throwable exp2) {
                //noinspection CallToPrintStackTrace
                exp2.printStackTrace();
            }
        } finally {
            releaseBackChannel();
            setCorsHeader(pReq, pResp);
            if (json == null) {
                json = requestHandler.handleThrowable(new Exception("Internal error while handling an exception"));
            }
        }

        sendResponse(pResp, pReq, json);
    }

    private void releaseBackChannel() {
        BackChannelHolder.remove();
    }


    private void prepareBackChannel(HttpServletRequest pReq) {
        BackChannelHolder.set(new ServletBackChannel(pReq));
    }


    private JSONStructure handleSecurely(final ServletRequestHandler pReqHandler, final HttpServletRequest pReq, final HttpServletResponse pResp)
            throws IOException, PrivilegedActionException, EmptyResponseException {
        Subject subject = (Subject) pReq.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE);
        if (subject != null) {
            try {
                return Subject.doAs(subject, (PrivilegedExceptionAction<JSONStructure>) () -> pReqHandler.handleRequest(pReq, pResp));
            } catch (PrivilegedActionException exp) {
                // Unwrap an empty response exception
                Throwable innerExp = exp.getCause();
                if (innerExp instanceof EmptyResponseException) {
                    throw (EmptyResponseException) innerExp;
                } else if (innerExp instanceof BadRequestException) {
                    throw (BadRequestException) innerExp;
                } else {
                    throw exp;
                }
            }
        } else {
            return pReqHandler.handleRequest(pReq, pResp);
        }
    }

    private String getOriginOrReferer(HttpServletRequest pReq) {
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

    private interface ServletRequestHandler {
        /**
         * Handle a request and return the answer as a JSON structure
         * @param pReq request arrived
         * @param pResp response to return
         * @return the JSON representation for the answer
         * @throws IOException if handling of an input or output stream failed
         */
        JSONStructure handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                throws IOException, EmptyResponseException;
    }

    // factory method for POST request handler
    private ServletRequestHandler newPostHttpRequestHandler() {
        return new ServletRequestHandler() {
            /** {@inheritDoc} */
             public JSONStructure handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                     throws IOException, EmptyResponseException {
                 String encoding = pReq.getCharacterEncoding();
                 InputStream is = pReq.getInputStream();
                 return requestHandler.handlePostRequest(pReq.getRequestURI(),is, encoding, getParameterMap(pReq));
             }
        };
    }

    // factory method for GET request handler
    private ServletRequestHandler newGetHttpRequestHandler() {
        return new ServletRequestHandler() {
            /** {@inheritDoc} */
            public JSONStructure handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                    throws EmptyResponseException {
                return requestHandler.handleGetRequest(pReq.getRequestURI(),pReq.getPathInfo(), getParameterMap(pReq));
            }
        };
    }

    // =======================================================================

    // Get parameter map either directly from a Servlet 2.4 compliant implementation
    // or by looking it up explictely (thanks to codewax for the patch)
    private Map<String, String[]> getParameterMap(HttpServletRequest pReq){
        try {
            // Servlet 2.4 API
            return pReq.getParameterMap();
        } catch (UnsupportedOperationException exp) {
            // Thrown by 'pseudo' 2.4 Servlet API implementations which fake a 2.4 API
            // As a service for the parameter map is build up explicitely
            Map<String, String[]> ret = new HashMap<>();
            Enumeration<String> params = pReq.getParameterNames();
            while (params.hasMoreElements()) {
                String param = params.nextElement();
                ret.put(param, pReq.getParameterValues(param));
            }
            return ret;
        }
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

    private void validateCallbackIfGiven(HttpServletRequest pReq) {
        String callback = pReq.getParameter(ConfigKey.CALLBACK.getKeyValue());
        if (callback != null && !MimeTypeUtil.isValidCallback(callback)) {
            throw new IllegalArgumentException("Invalid callback name given, which must be a valid javascript function name");
        }
    }
    private void sendStreamingResponse(HttpServletResponse pResp, String pCallback, JSONStructure pJson) throws IOException {
        Writer writer = new OutputStreamWriter(pResp.getOutputStream(), StandardCharsets.UTF_8);
        IoUtil.streamResponseAndClose(writer, pJson, pCallback);
    }

    private void setNoCacheHeaders(HttpServletResponse pResp) {
        pResp.setHeader("Cache-Control", "no-cache");
        pResp.setHeader("Pragma","no-cache");
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
