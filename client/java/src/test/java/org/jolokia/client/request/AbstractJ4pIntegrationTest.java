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


import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.jolokia.client.BasicAuthenticator;
import org.jolokia.client.J4pClient;
import org.jolokia.it.core.ItSetup;
import org.jolokia.server.core.http.AgentServlet;
import org.jolokia.test.util.EnvTestUtil;
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
            UriCompliance jolokiaCompliance = UriCompliance.DEFAULT.with("JOLOKIA", UriCompliance.Violation.AMBIGUOUS_EMPTY_SEGMENT);
            ((HttpConnectionFactory) jettyServer.getConnectors()[0].getDefaultConnectionFactory())
                    .getHttpConfiguration().setUriCompliance(jolokiaCompliance);
            ServletContextHandler jettyContext = new ServletContextHandler(jettyServer, "/");
            ServletHolder holder = new ServletHolder(new AgentServlet());
            jettyContext.addServlet(holder, "/j4p/*");

            SecurityHandler securityHandler = createSecurityHandler();
            jettyContext.setSecurityHandler(securityHandler);

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

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        UserStore userStore = new UserStore();
        userStore.addUser("jolokia", new Password("jolokia"), new String[] { "jolokia" });
        securityHandler.setRealmName("jolokia");
        HashLoginService loginService = new HashLoginService("jolokia");
        loginService.setUserStore(userStore);
        securityHandler.setLoginService(loginService);
        securityHandler.setConstraintMappings(new ConstraintMapping[]{cm});

        securityHandler.setAuthenticator(new org.eclipse.jetty.security.authentication.BasicAuthenticator());

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
