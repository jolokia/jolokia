package org.jolokia.mule;

import org.jolokia.http.AgentServlet;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.HandlerContainer;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.*;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mule.AbstractAgent;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.lifecycle.StartException;
import org.mule.api.lifecycle.StopException;

import java.net.Inet4Address;
import java.net.UnknownHostException;
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
 * @author roland
 * @since Dec 8, 2009
 */
public class JolokiaMuleAgent extends AbstractAgent {

    // Jetty server to use
    private Server server;

    // Default port
    private int port = 8888;

    // User/Password used for accessing the
    // agent
    private String user;
    private String password;

    // Initialisation parameter
    private boolean debug = false;
    private int historyMaxEntries = 10;
    private int debugMaxEntries = 100;
    private int maxDepth = 5;
    private int maxCollectionSize = 0;
    private int maxObjects = 10000;

    protected JolokiaMuleAgent() {
        super("jolokia-agent");
        server = null;
    }

    public void stop() throws MuleException {
        try {
            server.stop();
        } catch (Exception e) {
            throw new StopException(e,this);
        }
    }

    public void start() throws MuleException {
        try {
            server.start();
        } catch (Exception e) {
            throw new StartException(e,this);
        }
    }

    @Override
    public String getDescription() {
        String host;
        try {
            host = Inet4Address.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "localhost";
        }
        return "jolokia Agent: http://" + host + ":" + getPort() + "/jolokia";
    }

    public void dispose() {
    }

    public void registered() {
    }

    public void unregistered() {
    }

    @Override
    public void initialise() throws InitialisationException {
        server = getServer(getPort());
        Context root = getContext(server);
        ServletHolder servletHolder = getServletHolder();
        root.addServlet(servletHolder, "/*");
    }

    private Server getServer(int pPort) {
        Server newServer = new Server();

        Connector connector = new SelectChannelConnector();
        connector.setPort(pPort);
        newServer.setConnectors(new Connector[]{connector});

        return newServer;
    }


    private ServletHolder getServletHolder() {
        ServletHolder holder = new ServletHolder(new AgentServlet());
        holder.setInitParameters(getInitParameters());
        holder.setInitOrder(1);
        return holder;
    }

    private Context getContext(HandlerContainer pContainer) {
        Context root = new Context(pContainer,"/jolokia",Context.SESSIONS);
        if (getUser() != null && getPassword() != null) {
            root.setSecurityHandler(getSecurityHandler(getUser(),getPassword(),"jolokia-role"));
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
        realm.addUserToRole(getUser(),pRole);
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

    private Map getInitParameters() {
        Map ret = new HashMap();
        ret.put("debugMaxEntries","" + getDebugMaxEntries());
        ret.put("historyMaxEntries","" + getHistoryMaxEntries());
        ret.put("maxCollectionsSize","" + getMaxCollectionSize());
        ret.put("maxDepth","" + getMaxDepth());
        ret.put("maxObjects","" + getMaxObjects());
        ret.put("debug","" + isDebug());
        return ret;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int pPort) {
        port = pPort;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String pUser) {
        user = pUser;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String pPassword) {
        password = pPassword;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean pDebug) {
        debug = pDebug;
    }

    public int getHistoryMaxEntries() {
        return historyMaxEntries;
    }

    public void setHistoryMaxEntries(int pHistoryMaxEntries) {
        historyMaxEntries = pHistoryMaxEntries;
    }

    public int getDebugMaxEntries() {
        return debugMaxEntries;
    }

    public void setDebugMaxEntries(int pDebugMaxEntries) {
        debugMaxEntries = pDebugMaxEntries;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int pMaxDepth) {
        maxDepth = pMaxDepth;
    }

    public int getMaxCollectionSize() {
        return maxCollectionSize;
    }

    public void setMaxCollectionSize(int pMaxCollectionSize) {
        maxCollectionSize = pMaxCollectionSize;
    }

    public int getMaxObjects() {
        return maxObjects;
    }

    public void setMaxObjects(int pMaxObjects) {
        maxObjects = pMaxObjects;
    }

}
