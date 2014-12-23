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

import org.jolokia.client.J4pClient;
import org.jolokia.client.BasicAuthenticator;
import org.jolokia.http.AgentServlet;
import org.jolokia.it.ItSetup;
import org.jolokia.test.util.EnvTestUtil;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.security.*;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * @author roland
 * @since Apr 26, 2010
 */
abstract public class AbstractJ4pIntegrationTest {

    private Server jettyServer;

    protected ItSetup itSetup;


    protected String j4pUrl;

    // Client which can be used by subclasses for testing
    protected J4pClient j4pClient;

    @BeforeClass
    public void start() throws Exception {
        String testUrl = System.getProperty("j4p.url");
        itSetup = new ItSetup();
        if (testUrl == null) {

            int port = EnvTestUtil.getFreePort();
            jettyServer = new Server(port);
            Context jettyContext = new Context(jettyServer, "/");
            ServletHolder holder = new ServletHolder(new AgentServlet());
            holder.setInitParameter("dispatcherClasses", "org.jolokia.jsr160.Jsr160RequestDispatcher");
            jettyContext.addServlet(holder, "/j4p/*");

            SecurityHandler securityHandler = createSecurityHandler();
            jettyContext.addHandler(securityHandler);

            jettyServer.start();
            j4pUrl = "http://localhost:" + port + "/j4p";
            // Start the integration MBeans
            itSetup.start();
        } else {
            j4pUrl = testUrl;
        }
        j4pClient = createJ4pClient(j4pUrl);
	}

    private SecurityHandler createSecurityHandler() {
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"jolokia"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        SecurityHandler securityHandler = new SecurityHandler();
        HashUserRealm realm = new HashUserRealm("Jolokia");
        realm.put("jolokia","jolokia");
        realm.addUserToRole("jolokia", "jolokia");
        securityHandler.setUserRealm(realm);
        securityHandler.setConstraintMappings(new ConstraintMapping[]{cm});
        return securityHandler;
    }

    protected J4pClient createJ4pClient(String url) {
        return J4pClient.url(url)
                .user("jolokia")
                .password("jolokia")
                .authenticator(new BasicAuthenticator().preemptive())
                .pooledConnections()
                .build();
    }


    protected void startWithoutAgent() throws Exception {
        String testUrl = System.getProperty("j4p.url");
        itSetup = new ItSetup();
        if (testUrl == null) {
            int port = EnvTestUtil.getFreePort();
            jettyServer = new Server(port);
            jettyServer.start();
            j4pUrl = "http://localhost:" + port + "/j4p";
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
