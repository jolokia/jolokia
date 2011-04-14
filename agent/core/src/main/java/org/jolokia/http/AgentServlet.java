package org.jolokia.http;

import org.jolokia.backend.BackendManager;
import org.jolokia.util.ConfigKey;
import org.jolokia.restrictor.RestrictorFactory;
import org.jolokia.restrictor.*;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import javax.management.*;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
 * Agent servlet which connects to a local JMX MBeanServer for
 * JMX operations.
 *
 * <p>
 * It uses a REST based approach which translates a GET Url into a
 * request. See the <a href="http://www.jolokia.org/reference/index.html>reference documentation</a>
 * for a detailed description of this servlet's features.
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

    @Override
    /**
     * Initialize the backend systems, the log handler and the restrictor. A subclass can tune
     * this step by overriding {@link #createRestrictor(String)} and {@link #createLogHandler(ServletConfig)}
     */
    public void init(ServletConfig pServletConfig) throws ServletException {
        super.init(pServletConfig);

        // Create a log handler early in the lifecycle, but not too early
        logHandler = createLogHandler(pServletConfig);

        // Different HTTP request handlers
        httpGetHandler = newGetHttpRequestHandler();
        httpPostHandler = newPostHttpRequestHandler();

        Map<ConfigKey,String> config = servletConfigAsMap(pServletConfig);
        if (restrictor == null) {
            restrictor = createRestrictor(ConfigKey.POLICY_LOCATION.getValue(config));
        } else {
            logHandler.info("Using custom access restriction provided by " + restrictor);
        }
        backendManager = new BackendManager(config,logHandler, restrictor);
        requestHandler = new HttpRequestHandler(backendManager,logHandler);
    }


    /**
     * Create a log handler using this servlet's logging facility for logging. This method can be overridden
     * to provide a custom log handler. This method is called before {@link #createRestrictor(String)} so the log handler
     * can already be used when building up the restrictor.
     *
     * @return a default log handlera
     * @param pServletConfig
     */
    protected LogHandler createLogHandler(ServletConfig pServletConfig) {
        return new LogHandler() {
            public void debug(String message) {
                log(message);
            }

            public void info(String message) {
                log(message);
            }

            public void error(String message, Throwable t) {
                log(message,t);
            }
        };
    }

    @Override
    public void destroy() {
        backendManager.destroy();
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(httpGetHandler,req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(httpPostHandler,req,resp);
    }

    @SuppressWarnings({ "PMD.AvoidCatchingThrowable", "PMD.AvoidInstanceofChecksInCatchClause" })
    private void handle(ServletRequestHandler pReqHandler,HttpServletRequest pReq, HttpServletResponse pResp) throws IOException {
        JSONAware json = null;
        try {
            // Check access policy
            requestHandler.checkClientIPAccess(pReq.getRemoteHost(),pReq.getRemoteAddr());

            // Dispatch for the proper HTTP request method
            json = pReqHandler.handleRequest(pReq,pResp);
            if (backendManager.isDebug()) {
                backendManager.debug("Response: " + json);
            }
        } catch (Throwable exp) {
            JSONObject error = requestHandler.handleThrowable(
                    exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp);
            json = error;
        } finally {
            String callback = pReq.getParameter(ConfigKey.CALLBACK.getKeyValue());
            if (callback != null) {
                // Send a JSONP response
                sendResponse(pResp, "text/javascript",callback + "(" + json.toJSONString() +  ");");
            } else {
                sendResponse(pResp, "text/plain",json.toJSONString());
            }
        }
    }

    private interface ServletRequestHandler {
        JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                throws IOException;
    }


    private ServletRequestHandler newPostHttpRequestHandler() {
        return new ServletRequestHandler() {
            public JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                    throws IOException {
                String encoding = pReq.getCharacterEncoding();
                InputStream is = pReq.getInputStream();
                return requestHandler.handlePostRequest(pReq.getRequestURI(),is, encoding,pReq.getParameterMap());
            }
        };
    }

    private ServletRequestHandler newGetHttpRequestHandler() {
        return new ServletRequestHandler() {
            public JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp) {
                return requestHandler.handleGetRequest(pReq.getRequestURI(),pReq.getPathInfo(),pReq.getParameterMap());
            }
        };
    }
    // =======================================================================

    private Map<ConfigKey, String> servletConfigAsMap(ServletConfig pConfig) {
        Enumeration e = pConfig.getInitParameterNames();
        Map<ConfigKey,String> ret = new HashMap<ConfigKey, String>();
        while (e.hasMoreElements()) {
            String keyS = (String) e.nextElement();
            ConfigKey key = ConfigKey.getGlobalConfigKey(keyS);
            if (key != null) {
                ret.put(key,pConfig.getInitParameter(keyS));
            }
        }
        return ret;
    }

    private void sendResponse(HttpServletResponse pResp, String pContentType, String pJsonTxt) throws IOException {
        try {
            pResp.setCharacterEncoding("utf-8");
            pResp.setContentType(pContentType);
        } catch (NoSuchMethodError error) {
            // For a Servlet 2.3 container, set the charset by hand
            pResp.setContentType(pContentType + "; charset=utf-8");
        }
        pResp.setStatus(200);
        PrintWriter writer = pResp.getWriter();
        writer.write(pJsonTxt);
    }

}
