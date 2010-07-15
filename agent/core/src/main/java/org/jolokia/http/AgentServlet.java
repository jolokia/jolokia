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

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    private void handle(ServletRequestHandler pReqHandler,HttpServletRequest pReq, HttpServletResponse pResp) throws IOException {
        JSONAware json = null;
        int code = 200; // Success
        try {
            // Check access policy
            requestHandler.checkClientIPAccess(pReq.getRemoteHost(),pReq.getRemoteAddr());

            // Dispatch for the proper HTTP request method
            json = pReqHandler.handleRequest(pReq,pResp);
            code = requestHandler.extractResultCode(json);
            if (backendManager.isDebug()) {
                backendManager.info("Response: " + json);
            }
        } catch (RuntimeMBeanException exp) {
            JSONObject error = requestHandler.handleThrowable(exp.getTargetException());
            code = (Integer) error.get("status");
            json = error;
        } catch (Throwable exp) {
            JSONObject error = requestHandler.handleThrowable(exp);
            code = (Integer) error.get("status");
            json = error;
        } finally {
            sendResponse(pResp,code,json.toJSONString());
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
                if (backendManager.isDebug()) {
                    logHandler.debug("URI: " + pReq.getRequestURI());
                    logHandler.debug("Path-Info: " + pReq.getPathInfo());
                }
                String encoding = pReq.getCharacterEncoding();
                InputStream is = pReq.getInputStream();
                return requestHandler.handleRequestInputStream(is, encoding);
            }
        };
    }

    private ServletRequestHandler newGetHttpRequestHandler() {
        return new ServletRequestHandler() {
            public JSONAware handleRequest(HttpServletRequest pReq, HttpServletResponse pResp) {
                JmxRequest jmxReq =
                        JmxRequestFactory.createRequestFromUrl(pReq.getPathInfo(),pReq.getParameterMap());
                if (backendManager.isDebug() && !"debugInfo".equals(jmxReq.getOperation())) {
                    logHandler.debug("URI: " + pReq.getRequestURI());
                    logHandler.debug("Path-Info: " + pReq.getPathInfo());
                    logHandler.debug("Request: " + jmxReq.toString());
                }
                return requestHandler.executeRequest(jmxReq);
            }
        };
    }
    // =======================================================================

    private Map<ConfigKey, String> servletConfigAsMap(ServletConfig pConfig) {
        Enumeration e = pConfig.getInitParameterNames();
        Map<ConfigKey,String> ret = new HashMap<ConfigKey, String>();
        while (e.hasMoreElements()) {
            String keyS = (String) e.nextElement();
            ConfigKey key = ConfigKey.getByKey(keyS);
            if (key != null) {
                ret.put(key,pConfig.getInitParameter(keyS));
            }
        }
        return ret;
    }

    private void sendResponse(HttpServletResponse pResp, int pStatusCode, String pJsonTxt) throws IOException {
        try {
            pResp.setCharacterEncoding("utf-8");
            pResp.setContentType("text/plain");
        } catch (NoSuchMethodError error) {
            // For a Servlet 2.3 container, set the charset by hand
            pResp.setContentType("text/plain; charset=utf-8");
        }
        pResp.setStatus(pStatusCode);
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
