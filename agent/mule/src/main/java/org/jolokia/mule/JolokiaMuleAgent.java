package org.jolokia.mule;

import java.io.IOException;

import org.jolokia.util.NetworkUtil;
import org.mule.AbstractAgent;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.*;

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
 * Jolokia agent for the Mule ESB, which works with Mule's agent
 * API for version 2 and 3.
 *
 * @author roland
 * @since Dec 8, 2009
 */
public class JolokiaMuleAgent extends AbstractAgent implements MuleAgentConfig {

    // Internal HTTP-Server
    protected MuleAgentHttpServer server;

    protected JolokiaMuleAgent() {
        super("jolokia-agent");
    }

    // =====================================================================================
    // Lifecycle methods

    /**
     * Lifecycle method called by mule during startup
     *
     * @throws MuleException if something fails
     */
    public void start() throws MuleException {
        if (server == null) {
            throw new StartException(
                    new IllegalStateException("Cannot start the HTTP server since this context is not initialized"),
                    this);
        }
        server.start();
    }

    /**
     * Lifecycle hook called by Mule while shuttding down the agent
     *
     * @throws MuleException if something fails
     */
    public void stop() throws MuleException {
        if (server == null) {
            throw new StopException(
                    new IllegalStateException("Cannot stop the HTTP server since this context is not initialized"),
                    this);
        }
        server.stop();
    }

    /**
     * Description including agent URL
     *
     * @return agent url
     */
    @Override
    public String getDescription() {
        String hostDescr = host;
        try {
            if (hostDescr == null) {
                hostDescr = NetworkUtil.getLocalAddress().getHostName();
            }
        } catch (IOException e) {
            hostDescr = "localhost";
        }
        return "Jolokia Agent: http://" + hostDescr + ":" + getPort() + "/jolokia";
    }

    /**
     * Unused lifecycle hook
     */
    public void dispose() {
    }

    /**
     * Lifecycle hook for Mule 2, unused
     */
    public void registered() {
    }

    /**
     * Lifecycle hook for Mule 2, unused
     */
    public void unregistered() {
    }

    /**
     * Initialise the agent and start up an internal jetty server
     *
     * @throws InitialisationException
     */
    public void initialise() throws InitialisationException {
        server = MuleAgentHttpServerFactory.create(this, this);
    }

    // ===============================================================================
    // Configuration parameters

    // User/Password used for accessing the
    // agent
    private String user;
    private String password;

    // Port and Host to use
    private String host = null;
    private int port = 8888;

    // Initialisation parameter
    private boolean debug = false;
    private int historyMaxEntries = 10;
    private int debugMaxEntries = 100;
    private int maxDepth = 15;
    private int maxCollectionSize = 0;
    private int maxObjects = 0;

    public String getHost() {
        return host;
    }

    public void setHost(String pHost) {
        host = pHost;
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
