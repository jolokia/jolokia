package org.jolokia.http;

import java.io.*;
import java.util.*;

import javax.management.RuntimeMBeanException;
import javax.servlet.*;
import javax.servlet.http.*;

import org.jolokia.backend.BackendManager;
import org.jolokia.config.*;
import org.jolokia.restrictor.*;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONAware;

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

    // Backend dispatcher
    private BackendManager backendManager;

    // Used for logging
    private LogHandler logHandler;

    // Request handler for parsing request parameters and building up a response
    private HttpRequestHandler requestHandler;

    // Restrictor to use as given in the constructor
    private Restrictor restrictor;
    
    // Mime type used for returning the answer
    private String configMimeType;

    /**
     * No argument constructor, used e.g. by an servlet
     * descriptor when creating the servlet out of web.xml
     */
    public AgentServlet() {
        this(null);
    }

    /**
     * Constructor taking a restrictor to use
     *
     * @param pRestrictor restrictor to use or <code>null</code> if the restrictor
     *        should be created in the default way ({@link #createRestrictor(String)})
     */
    public AgentServlet(Restrictor pRestrictor) {
        restrictor = pRestrictor;
    }

    /**
     * Get the installed log handler
     *
     * @return loghandler used for logging.
     */
    protected LogHandler getLogHandler() {
        return logHandler;
    }

    /**
     * Create a restrictor restrictor to use. By default, a policy file
     * is looked up (with the URL given by the init parameter {@link ConfigKey#POLICY_LOCATION}
     * or "/jolokia-access.xml" by default) and if not found an {@link AllowAllRestrictor} is
     * used by default. This method is called during the {@link #init(ServletConfig)} when initializing
     * the subsystems and can be overridden for custom restrictor creation.
     *
     * @param pLocation location to lookup the restrictor
     * @return the restrictor to use.
     */
    protected Restrictor createRestrictor(String pLocation) {
        LogHandler log = getLogHandler();
        try {
            Restrictor newRestrictor = RestrictorFactory.lookupPolicyRestrictor(pLocation);
            if (newRestrictor != null) {
                log.info("Using access restrictor " + pLocation);
                return newRestrictor;
            } else {
                log.info("No access restrictor found at " + pLocation + ", access to all MBeans is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            log.error("Error while accessing access restrictor at " + pLocation +
                              ". Denying all access to MBeans for security reasons. Exception: " + e, e);
            return new DenyAllRestrictor();
        }
    }

    /**
     * Initialize the backend systems, the log handler and the restrictor. A subclass can tune
     * this step by overriding {@link #createRestrictor(String)} and {@link #createLogHandler(ServletConfig)}
     *
     * @param pServletConfig servlet configuration
     */
    @Override
    public void init(ServletConfig pServletConfig) throws ServletException {
        super.init(pServletConfig);

        // Create a log handler early in the lifecycle, but not too early
        logHandler = createLogHandler(pServletConfig);

        // Different HTTP request handlers
        httpGetHandler = newGetHttpRequestHandler();
        httpPostHandler = newPostHttpRequestHandler();

        Configuration config = initConfig(pServletConfig);
        if (restrictor == null) {
            restrictor = createRestrictor(config.get(ConfigKey.POLICY_LOCATION));
        } else {
            logHandler.info("Using custom access restriction provided by " + restrictor);
        }
        configMimeType = config.get(ConfigKey.MIME_TYPE);
        backendManager = new BackendManager(config,logHandler, restrictor);
        requestHandler = new HttpRequestHandler(config,backendManager,logHandler);
    }


    /**
     * Create a log handler using this servlet's logging facility for logging. This method can be overridden
     * to provide a custom log handler. This method is called before {@link #createRestrictor(String)} so the log handler
     * can already be used when building up the restrictor.
     *
     * @return a default log handler
     * @param pServletConfig servlet config from where to get information to build up the log handler
     */
    protected LogHandler createLogHandler(ServletConfig pServletConfig) {
        return new LogHandler() {
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
        };
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        backendManager.destroy();
        super.destroy();
    }

    /** {@inheritDoc} */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(httpGetHandler,req, resp);
    }

    /** {@inheritDoc} */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(httpPostHandler,req,resp);
    }

    /**
     * OPTION requests are treated as CORS preflight requests
     *
     * @param req the original request
     * @param resp the response the answer are written to
     * */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String,String> responseHeaders =
                requestHandler.handleCorsPreflightRequest(
                        req.getHeader("Origin"),
                        req.getHeader("Access-Control-Request-Headers"));
        for (Map.Entry<String,String> entry : responseHeaders.entrySet()) {
            resp.setHeader(entry.getKey(),entry.getValue());
        }
    }

    @SuppressWarnings({ "PMD.AvoidCatchingThrowable", "PMD.AvoidInstanceofChecksInCatchClause" })
    private void handle(ServletRequestHandler pReqHandler,HttpServletRequest pReq, HttpServletResponse pResp) throws IOException {
        JSONAware json = null;
        try {
            // Check access policy
            requestHandler.checkClientIPAccess(pReq.getRemoteHost(),pReq.getRemoteAddr());

            // Dispatch for the proper HTTP request method
            json = pReqHandler.handleRequest(pReq,pResp);
        } catch (Throwable exp) {
            json = requestHandler.handleThrowable(
                    exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp
                    );
        } finally {
            setCorsHeader(pReq, pResp);

            String callback = pReq.getParameter(ConfigKey.CALLBACK.getKeyValue());
            String answer = json != null ?
                    json.toJSONString() :
                    requestHandler.handleThrowable(new Exception("Internal error while handling an exception")).toJSONString();
            if (callback != null) {
                // Send a JSONP response
                sendResponse(pResp, "text/javascript", callback + "(" + answer + ");");
            } else {
                sendResponse(pResp, getMimeType(pReq),answer);
            }
        }
    }

    // Set an appropriate CORS header if requested and if allowed
    private void setCorsHeader(HttpServletRequest pReq, HttpServletResponse pResp) {
        String origin = requestHandler.extractCorsOrigin(pReq.getHeader("Origin"));
        if (origin != null) {
            pResp.setHeader("Access-Control-Allow-Origin",origin);
            pResp.setHeader("Access-Control-Allow-Credentials","true");
        }
    }

    // Extract mime type for response (if not JSONP)
    private String getMimeType(HttpServletRequest pReq) {
        String requestMimeType = pReq.getParameter(ConfigKey.MIME_TYPE.getKeyValue());
        if (requestMimeType != null) {
            return requestMimeType;
        }
        return configMimeType;
    }

    private interface ServletRequestHandler {
        /**
         * Handle a request and return the answer as a JSON structure
         * @param pReq request arrived
         * @param pResp response to return
         * @return the JSON representation for the answer
         * @throws IOException if handling of an input or output stream failed
         */
        JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                throws IOException;
    }

    // factory method for POST request handler
    private ServletRequestHandler newPostHttpRequestHandler() {
        return new ServletRequestHandler() {
            /** {@inheritDoc} */
             public JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                    throws IOException {
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
            public JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp) {
                return requestHandler.handleGetRequest(pReq.getRequestURI(),pReq.getPathInfo(), getParameterMap(pReq));
            }
        };
    }

    // =======================================================================

    // Get parameter map either directly from an Servlet 2.4 compliant implementation
    // or by looking it up explictely (thanks to codewax for the patch)
    private Map<String, String[]> getParameterMap(HttpServletRequest pReq){
        try {
            // Servlet 2.4 API
            return pReq.getParameterMap();
        } catch (UnsupportedOperationException exp) {
            // Thrown by 'pseudo' 2.4 Servlet API implementations which fake a 2.4 API
            // As a service for the parameter map is build up explicitely
            Map<String, String[]> ret = new HashMap<String, String[]>();
            Enumeration params = pReq.getParameterNames();
            while (params.hasMoreElements()) {
                String param = (String) params.nextElement();
                ret.put(param, pReq.getParameterValues(param));
            }
            return ret;
        }
    }

    // Examines servlet config and servlet context for configuration parameters.
    // Configuration from the servlet context overrides servlet parameters defined in web.xml
    Configuration initConfig(ServletConfig pConfig) {
        Configuration config = new Configuration();
        // From ServletContext ....
        config.updateGlobalConfiguration(new ServletConfigFacade(pConfig));
        // ... and ServletConfig
        config.updateGlobalConfiguration(new ServletContextFacade(getServletContext()));
        return config;
    }

    private void sendResponse(HttpServletResponse pResp, String pContentType, String pJsonTxt) throws IOException {
        setContentType(pResp, pContentType);
        pResp.setStatus(200);
        setNoCacheHeaders(pResp);
        PrintWriter writer = pResp.getWriter();
        writer.write(pJsonTxt);
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
        // answers some times which seems to be an implementation peculiarity from Tomcat
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
        public Enumeration getNames() {
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
        public Enumeration getNames() {
            return servletContext.getInitParameterNames();
        }

        /** {@inheritDoc} */
        public String getParameter(String pName) {
            return servletContext.getInitParameter(pName);
        }
    }
}
