package org.jolokia.mule;

/*
 * Copyright 2009-2011 Roland Huss
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

import java.util.HashMap;
import java.util.Map;

import org.jolokia.http.AgentServlet;
import org.jolokia.util.ClassUtil;
import org.jolokia.util.NetworkUtil;
import org.mortbay.jetty.*;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.*;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mule.api.agent.Agent;
import org.mule.api.lifecycle.StartException;
import org.mule.api.lifecycle.StopException;

/**
 * HTTP Server for the Mule agent which encapsulates a Jetty server.
 *
 *
 * @author roland
 * @since 30.08.11
 */
public class MortbayMuleAgentHttpServer implements MuleAgentHttpServer {

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
    MortbayMuleAgentHttpServer(Agent pParent, MuleAgentConfig pConfig) {
        parent = pParent;

        // Initialise server
        server = getServer(pConfig);
        Context root = getContext(server, pConfig);
        ServletHolder servletHolder = getServletHolder(pConfig);
        root.addServlet(servletHolder, "/*");
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

    private Context getContext(HandlerContainer pContainer, MuleAgentConfig pConfig) {
        Context root = new Context(pContainer, "/jolokia", Context.SESSIONS);
        if (pConfig.getUser() != null && pConfig.getPassword() != null) {
            root.setSecurityHandler(getSecurityHandler(pConfig.getUser(), pConfig.getPassword(), "jolokia-role"));
        }
        return root;
    }

    private SecurityHandler getSecurityHandler(String pUser, String pPassword, String pRole) {
        SecurityHandler securityHandler = new SecurityHandler();
        securityHandler.setConstraintMappings(getConstraintMappings(pRole));
        securityHandler.setUserRealm(getUserRealm(pUser, pPassword, pRole));
        return securityHandler;
    }

    private UserRealm getUserRealm(String pUser, String pPassword, String pRole) {
        HashUserRealm realm = new HashUserRealm("jolokia Realm");
        realm.put(pUser,pPassword);
        realm.addUserToRole(pUser,pRole);
        return realm;
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

}
