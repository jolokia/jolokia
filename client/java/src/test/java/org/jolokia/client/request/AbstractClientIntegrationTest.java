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
package org.jolokia.client.request;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.EnumSet;
import java.util.Properties;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.security.Password;
import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.it.core.ItSetup;
import org.jolokia.server.core.http.AgentServlet;
import org.jolokia.test.util.EnvTestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * <p>Abstract base class for all the tests that should communicate with real HTTP server based on Jetty 12.</p>
 *
 * @author roland
 * @since Apr 26, 2010
 */
abstract public class AbstractClientIntegrationTest {

    public static final Logger LOG = LoggerFactory.getLogger(AbstractClientIntegrationTest.class);
    private static String jettyVersion;

    private Server jettyServer;

    protected ItSetup itSetup;

    protected String jolokiaUrl;
    protected static String externalUrl;

    // Client which can be used by subclasses for testing
    protected JolokiaClient jolokiaClient;

    static {
        externalUrl = System.getProperty("j4p.url");
    }

    @BeforeClass
    public void start() throws Exception {
        checkJettyVersion();

        if (externalUrl == null) {
            int port = EnvTestUtil.getFreePort();
            jettyServer = new Server(port);
            UriCompliance jolokiaCompliance = UriCompliance.DEFAULT
                .with("JOLOKIA",
                    UriCompliance.Violation.SUSPICIOUS_PATH_CHARACTERS,
                    UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING,
                    UriCompliance.Violation.AMBIGUOUS_EMPTY_SEGMENT);
            ((HttpConnectionFactory) jettyServer.getConnectors()[0].getDefaultConnectionFactory())
                    .getHttpConfiguration().setUriCompliance(jolokiaCompliance);
            ServletContextHandler jettyContext = new ServletContextHandler("/");
            jettyContext.getServletHandler().setDecodeAmbiguousURIs(true);
            ServletHolder holder = new ServletHolder(new AgentServlet());
            holder.setInitParameter("includeStackTrace", "true");
            jettyContext.addServlet(holder, "/j4p/*");
            jettyContext.addFilter(new FilterHolder(new HttpFilter() {
                @Override
                protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws IOException, ServletException {
                    LOG.info("[{}:{}]> {}: {} (Agent: {})",
                        req.getRemoteAddr(), req.getRemotePort(), req.getMethod(), req.getRequestURI(), req.getHeader("User-Agent"));
                    chain.doFilter(req, res);
                }
            }), "/j4p/*", EnumSet.of(DispatcherType.REQUEST));

            SecurityHandler securityHandler = createSecurityHandler();
            jettyContext.setSecurityHandler(securityHandler);

            jettyContext.setErrorHandler(new ErrorHandler() {
                @Override
                protected void generateResponse(Request request, Response response, int code, String message, Throwable cause, Callback callback) throws IOException {
                    super.generateResponse(request, response, code, message, cause, callback);
                    LOG.error("{} < {}", request.getHttpURI(), message, cause);
                }
            });

            jettyServer.setHandler(jettyContext);
            jettyServer.start();

            jolokiaUrl = "http://localhost:" + port + "/j4p";
            LOG.info("Started Jetty Server ({}). Jolokia available at {}", jettyVersion, jolokiaUrl);

            // Start the integration MBeans
            itSetup = new ItSetup();
            itSetup.start();
        } else {
            jolokiaUrl = externalUrl;
        }
        jolokiaClient = createJolokiaClient(jolokiaUrl);
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

    private static void checkJettyVersion() {
        try (InputStream is = AbstractClientIntegrationTest.class.getResourceAsStream("/META-INF/maven/org.eclipse.jetty/jetty-server/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                jettyVersion = props.getProperty("version");
            }
        } catch (Exception ignored) {
            jettyVersion = "unknown";
        }
    }

    /**
     * Prepare security configuration matching this {@code web.xml} fragment:<pre>{@code
     * <login-config>
     *   <auth-method>BASIC</auth-method>
     *   <realm-name>jolokia</realm-name>
     * </login-config>
     * <security-constraint>
     *   <web-resource-collection>
     *     <web-resource-name>Jolokia Security</web-resource-name>
     *     <url-pattern>/*</url-pattern>
     *   </web-resource-collection>
     *   <auth-constraint>
     *     <role-name>jolokia</role-name>
     *   </auth-constraint>
     * </security-constraint>
     * <security-role>
     *   <role-name>jolokia</role-name>
     * </security-role>
     * }</pre>
     *
     * @return
     */
    private SecurityHandler createSecurityHandler() {
        Constraint constraint = new Constraint.Builder(Constraint.ALLOWED_ANY_TRANSPORT)
            .name("Jolokia Security")
            .roles("jolokia")
            .authorization(Constraint.Authorization.SPECIFIC_ROLE)
            .build();

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        UserStore userStore = new UserStore();
        userStore.addUser("jolokia", new Password("jolokia"), new String[]{"jolokia"});
        HashLoginService loginService = new HashLoginService("jolokia");
        loginService.setUserStore(userStore);

        securityHandler.setRealmName("jolokia");
        securityHandler.setLoginService(loginService);
        securityHandler.setConstraintMappings(new ConstraintMapping[]{cm});
        securityHandler.setAuthenticator(new org.eclipse.jetty.security.authentication.BasicAuthenticator());

        return securityHandler;
    }

    protected JolokiaClient createJolokiaClient(String url) {
        return new JolokiaClientBuilder()
            .url(url)
            .user("jolokia")
            .password("jolokia")
            .connectionTimeout(3600000)
            .socketTimeout(3600000)
//            .pooledConnections()
            .build();
    }

    /**
     * For tests that want to start Jetty, but without Jolokia servlet registered.
     * @throws Exception
     */
    protected void startWithoutAgent() throws Exception {
        if (jettyServer.isStarted()) {
            throw new IllegalStateException("Jetty server should be stopped first.");
        }

        if (externalUrl == null) {
            int port = EnvTestUtil.getFreePort();

            jettyServer = new Server(port);
            jettyServer.start();

            String serverUrl = "http://localhost:" + port ;
            jolokiaUrl = serverUrl + "/j4p";
            LOG.info("Started Jetty Server ({}) without Jolokia at {}", jettyVersion, serverUrl);

            // Start the integration MBeans
            itSetup = new ItSetup();
            itSetup.start();
        } else {
            jolokiaUrl = externalUrl;
        }

        jolokiaClient = new JolokiaClient(URI.create(jolokiaUrl));
    }

    public String getJolokiaUrl() {
        return jolokiaUrl;
    }

    public JolokiaTargetConfig getTargetProxyConfig() {
        return new JolokiaTargetConfig("service:jmx:rmi:///jndi/rmi://localhost:45888/jmxrmi",null,null);
    }

}
