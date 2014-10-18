package org.jolokia.mule;

/*
 * Copyright 2014 Michio Nakagawa
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.jolokia.http.AgentServlet;
import org.jolokia.util.NetworkUtil;
import org.mule.api.agent.Agent;
import org.mule.api.lifecycle.StartException;
import org.mule.api.lifecycle.StopException;

/**
 * HTTP Server for the Mule agent which encapsulates a Eclipse Jetty server.
 *
 *
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public class EclipseMuleAgentHttpServer implements MuleAgentHttpServer {

    // parent agent
    private Agent parent;

    // Jetty server to use
    private Server server;

    /**
     * Constructor
     *
     * @param pParent parent for creating proper exceptions
     * @param pConfig configuration of the server
     */
    EclipseMuleAgentHttpServer(Agent pParent, MuleAgentConfig pConfig) {
        parent = pParent;

        // Initialize server
        server = getServer(pConfig);
        ServletContextHandler root = getContext(server, pConfig);
        ServletHolder servletHolder = getServletHolder(pConfig);
        root.addServlet(servletHolder,  "/*");
    }

    /**
     * Startup the HTTP server
     *
     * @throws StartException if starting fails
     */
    public void start() throws StartException {
        try {
            server.start();
        } catch (Exception e) {
            throw new StartException(e, parent);
        }
    }

    /**
     * Stop the internal HTTP server
     *
     * @throws StopException when stopping fails
     */
    public void stop() throws StopException {
        try {
            server.stop();
        } catch (Exception e) {
            throw new StopException(e, parent);
        }
    }

    // ======================================================================================

    // Create a Jetty Server with the agent servlet installed
    private Server getServer(MuleAgentConfig pConfig) {
        Server newServer = new Server();

        Connector connector = new SelectChannelConnector();
        if (pConfig.getHost() != null) {
            connector.setHost(pConfig.getHost());
        }
        connector.setPort(pConfig.getPort());
        newServer.setConnectors(new Connector[]{connector});

        return newServer;
    }

    private ServletHolder getServletHolder(MuleAgentConfig pConfig) {
        ServletHolder holder = new ServletHolder(new AgentServlet());
        holder.setInitParameters(getInitParameters(pConfig));
        holder.setInitOrder(1);
        return holder;
    }

    private ServletContextHandler getContext(HandlerContainer pContainer, MuleAgentConfig pConfig) {
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/jolokia");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new HelloServlet()), "/*");
        if (pConfig.getUser() != null && pConfig.getPassword() != null) {
        	context.setSecurityHandler(
        			getSecurityHandler(pConfig.getUser(), pConfig.getPassword(), "jolokia-role"));
        }
        return context;
    }

    private SecurityHandler getSecurityHandler(String pUser, String pPassword, String pRole) {
    	HashLoginService loginService = getLoginService(pUser, pPassword, pRole);
    	server.addBean(loginService);	 	   	
    	ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
    	securityHandler.setConstraintMappings(getConstraintMappings(pRole));
    	securityHandler.setAuthenticator(new BasicAuthenticator());
    	securityHandler.addBean(loginService);
        return securityHandler;
    }

    private HashLoginService getLoginService(String pUser, String pPassword, String pRole) {
    	Credential credential = Credential.getCredential(pPassword);
    	HashLoginService loginService = new HashLoginService("jolokia Realm");
    	loginService.putUser(pUser, credential, new String[] {pRole});
    	return loginService;
    }

    private ConstraintMapping[] getConstraintMappings(String ... pRoles) {
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(pRoles);
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");
        return new ConstraintMapping[] { cm };
    }

    private Map<String, String> getInitParameters(MuleAgentConfig pConfig) {
        Map<String,String> ret = new HashMap<String,String>();
        ret.put("debugMaxEntries", "" + pConfig.getDebugMaxEntries());
        ret.put("historyMaxEntries", "" + pConfig.getHistoryMaxEntries());
        ret.put("maxCollectionsSize", "" + pConfig.getMaxCollectionSize());
        ret.put("maxDepth", "" + pConfig.getMaxDepth());
        ret.put("maxObjects", "" + pConfig.getMaxObjects());
        ret.put("debug", "" + pConfig.isDebug());
        ret.put("agentType", "mule");
        ret.put("agentId", NetworkUtil.getAgentId(hashCode(), "mule"));
        return ret;
    }
    
    class HelloServlet extends HttpServlet {
		private static final long serialVersionUID = 1L;

		protected void doGet(HttpServletRequest request, HttpServletResponse response)
        		throws ServletException, IOException {
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("<h1>Hello Servlet</h1>");
            response.getWriter().println("session=" + request.getSession(true).getId());
        }
    }
}
