package org.jolokia.http;

import org.jolokia.backend.BackendManager;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.RestrictorFactory;
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

    /**
     * Set the log handler to use. This method must be called before
     * {@link #init(ServletConfig)} in order to have an effect, since
     * this log handler is used within init for initializing the subsytems
     * accordingly. Preferable this method is used in an overridden init method
     * in a sub class.
     *
     * @param pLogHandler log handler to use
     */
    protected void setLogHandler(LogHandler pLogHandler) {
        logHandler = pLogHandler;
    }

    /**
     * Create a restrictor restrictor to use. By default, a policy file
     * is looked up (with the URL given by the init parameter {@link ConfigKey#POLICY_LOCATION}
     * or "/jolokia-access.xml" by default) and if not found an {@link AllowAllRestrictor} is
     * used by default. This method is called during the {@link #init(ServletConfig)} when initializing
     * the subsystems and can be overridden for custom restrictor creation.
     *
     * @param pConfig agent configuration
     * @param pLogHandler a log handler which is used for sending out warnings e.g. when a fallback
     *        restrictor is used
     * @return the restrictor to use.
     */
    protected Restrictor createRestrictor(Map<ConfigKey, String> pConfig,LogHandler pLogHandler) {
        String location = ConfigKey.POLICY_LOCATION.getValue(pConfig);
        try {
            Restrictor restrictor = RestrictorFactory.lookupPolicyRestrictor(location);
            if (restrictor != null) {
                pLogHandler.info("Using access restrictor " + location);
                return restrictor;
            } else {
                pLogHandler.info("No access restrictor found at " + location + ", access to all MBeans is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            pLogHandler.error("Error while accessing access restrictor at " + location +
                                      ". Denying all access to MBeans for security reasons. Exception: " + e,e);
            return new DenyAllRestrictor();
        }
    }

    @Override
    public void init(ServletConfig pServletConfig) throws ServletException {
        super.init(pServletConfig);

        // Initialize a loghandler if not given already
        if (logHandler == null) {
            logHandler = getDefaultLogHandler();
        }

        // Different HTTP request handlers
        httpGetHandler = newGetHttpRequestHandler();
        httpPostHandler = newPostHttpRequestHandler();

        Map<ConfigKey,String> config = servletConfigAsMap(pServletConfig);
        backendManager = new BackendManager(config,logHandler, createRestrictor(config,logHandler));
        requestHandler = new HttpRequestHandler(backendManager,logHandler);
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
                backendManager.info("Response: " + json);
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



    // Default log handler using this servlet's logging facility for logging
    private LogHandler getDefaultLogHandler() {
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
}
