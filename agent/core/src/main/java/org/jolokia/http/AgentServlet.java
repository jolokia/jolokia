package org.jolokia.http;

import org.jolokia.*;
import org.jolokia.backend.BackendManager;
import org.jolokia.ConfigKey;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import javax.management.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
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
 * request. See {@link JmxRequest} for details about the URL format.
 * <p>
 * For now, only the request type
 * {@link org.jolokia.JmxRequest.Type#READ} for reading MBean
 * attributes is supported.
 * <p>
 * For the transfer via JSON only certain types are supported. Among basic types
 * like strings or numbers, collections, arrays and maps are also supported (which
 * translate into the corresponding JSON structure). Additional the OpenMBean types
 * {@link javax.management.openmbean.CompositeData} and {@link javax.management.openmbean.TabularData}
 * are supported as well. Refer to {@link org.jolokia.converter.json.ObjectToJsonConverter}
 * for additional information.
 ** 
 * @author roland@cpan.org
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

    public AgentServlet() {
        this(null);
    }

    public AgentServlet(LogHandler pLogHandler) {
        logHandler = pLogHandler != null ? pLogHandler : getDefaultLogHandler();
    }

    @Override
    public final void init(ServletConfig pConfig) throws ServletException {
        super.init(pConfig);

        // Different HTTP request handlers
        httpGetHandler = newGetHttpRequestHandler();
        httpPostHandler = newPostHttpRequestHandler();

        backendManager = new BackendManager(servletConfigAsMap(pConfig),logHandler);
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
                throws IOException, MalformedObjectNameException;
    }


    private ServletRequestHandler newPostHttpRequestHandler() {
        return new ServletRequestHandler() {
            public JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp)
                    throws IOException, MalformedObjectNameException {
                String encoding = pReq.getCharacterEncoding();
                InputStream is = pReq.getInputStream();
                return requestHandler.handlePostRequest(pReq.getRequestURI(),is, encoding);
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
