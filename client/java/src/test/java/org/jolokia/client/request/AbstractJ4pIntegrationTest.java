package org.jolokia.client.request;

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

import org.jolokia.server.core.http.AgentServlet;
import org.jolokia.client.J4pClient;
import org.jolokia.it.core.ItSetup;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.testng.annotations.*;

/**
 * @author roland
 * @since Apr 26, 2010
 */
abstract public class AbstractJ4pIntegrationTest {

    private Server jettyServer;

    protected ItSetup itSetup;

    private static final int JETTY_DEFAULT_PORT = 8234;
    private static final String SERVER_BASE_URL = "http://localhost:" + JETTY_DEFAULT_PORT;
    private static final String J4P_CONTEXT = "/j4p";

    protected static final String J4P_DEFAULT_URL = SERVER_BASE_URL + J4P_CONTEXT;

    private String j4pUrl;

    // Client which can be used by subclasses for testing
    protected J4pClient j4pClient;

    @BeforeClass
    public void start() throws Exception {
        String testUrl = System.getProperty("j4p.url");
        itSetup = new ItSetup();
        if (testUrl == null) {
            jettyServer = new Server(JETTY_DEFAULT_PORT);
            Context jettyContext = new Context(jettyServer, "/");
            ServletHolder holder = new ServletHolder(new AgentServlet());
            holder.setInitParameter("dispatcherClasses","org.jolokia.service.jsr160.Jsr160RequestHandler");
            jettyContext.addServlet(holder, J4P_CONTEXT + "/*");
            jettyServer.start();
            j4pUrl = J4P_DEFAULT_URL;
            // Start the integration MBeans
            itSetup.start();
        } else {
            j4pUrl = testUrl;
        }
        j4pClient = createJ4pClient(j4pUrl);
	}

    protected J4pClient createJ4pClient(String url) {
        return J4pClient.url(url).pooledConnections().build();
    }


    protected void startWithoutAgent() throws Exception {
        String testUrl = System.getProperty("j4p.url");
        itSetup = new ItSetup();
        if (testUrl == null) {
            jettyServer = new Server(JETTY_DEFAULT_PORT);
            jettyServer.start();
            j4pUrl = J4P_DEFAULT_URL;
            // Start the integration MBeans
            itSetup.start();
        } else {
            j4pUrl = testUrl;
        }
        j4pClient = new J4pClient(j4pUrl);
	}




    @AfterClass
	public void stop() throws Exception {
        if (itSetup != null) {
            itSetup.stop();
        }
		if (jettyServer != null) {
			jettyServer.stop();
		}
	}

    public String getJ4pUrl() {
        return j4pUrl;
    }

    public J4pTargetConfig getTargetProxyConfig() {
        return new J4pTargetConfig("service:jmx:rmi:///jndi/rmi://localhost:45888/jmxrmi",null,null);
    }
}
