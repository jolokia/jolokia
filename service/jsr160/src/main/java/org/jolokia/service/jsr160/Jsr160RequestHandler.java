/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jsr160;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.util.jmx.SingleMBeanServerAccess;
import org.jolokia.service.jmx.api.CommandHandler;
import org.jolokia.service.jmx.api.CommandHandlerManager;

/**
 * The <em>proxy</em> Jolokia {@link org.jolokia.server.core.service.request.RequestHandler}, which
 * invokes an operation on a single {@link MBeanServerConnection} using {@link JMXConnector}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class Jsr160RequestHandler extends AbstractRequestHandler {

    /** Each {@link JolokiaRequest} is handled by a dedicated {@link CommandHandler}. */
    private CommandHandlerManager commandHandlerManager;

    // Allowlist and denylist for patterns to match the JMX Service URL against
    private Set<String> allowList;
    private Set<String> denyList;

    /**
     * Create a new <em>proxy</em> request handler which accesses remote MBeans.
     *
     * @param pOrder service order as given during construction.
     */
    public Jsr160RequestHandler(int pOrder) {
        super("proxy", pOrder);
    }

    /**
     * Initialization
     *
     * @param pContext the jolokia context
     */
    @Override
    public void init(JolokiaContext pContext) {
        commandHandlerManager = new CommandHandlerManager(pContext, getProvider());
        allowList = extractAllowList(pContext);
        denyList = extractDenyList(pContext);
    }

    /**
     * The request can be handled when a target configuration is given. The provider namespace is optional
     * here for backwards compatibility.
     */
    @Override
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return pJolokiaRequest.getOption("target") instanceof JSONObject;
    }

    /**
     * Call a remote connector based on the connection information contained in
     * the request.
     *
     * @param pJmxReq the request to dispatch
     * @return result object
     * @throws InstanceNotFoundException
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     * @throws IOException
     */
    @Override
    public <R extends JolokiaRequest> Object handleRequest(R pJmxReq, Object pPreviousResult)
            throws IOException, JMException, JMRuntimeException, NotChangedException, BadRequestException, EmptyResponseException {

        CommandHandler<R> handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());

        JMXConnector connector = null;
        try {
            connector = createConnector(pJmxReq);
            connector.connect();
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            if (handler.handleAllServersAtOnce(pJmxReq)) {
                // There is no way to get remotely all MBeanServers ...
                MBeanServerAccess manager = new SingleMBeanServerAccess(connection);
                return handler.handleAllServerRequest(manager, pJmxReq, pPreviousResult);
            } else {
                // just one MBeanServerConnection to iterate, so propagate any exception
                return handler.handleSingleServerRequest(connection, pJmxReq);
            }
        } finally {
            releaseConnector(connector);
        }
    }

    // TODO: Add connector to a pool and release it on demand. For now, simply close it.
    private JMXConnector createConnector(JolokiaRequest pJmxReq) throws IOException {
        ProxyTargetConfig targetConfig = new ProxyTargetConfig(pJmxReq.getOption("target"));
        String urlS = targetConfig.getUrl();
        if (!acceptTargetUrl(urlS)) {
            throw new SecurityException(String.format("Target URL %s is not allowed by configuration", urlS));
        }

        JMXServiceURL url = new JMXServiceURL(urlS);

        Map<String, Object> env = prepareEnv(targetConfig.getEnv());
        return JMXConnectorFactory.newJMXConnector(url, env);
    }

    private void releaseConnector(JMXConnector pConnector) throws IOException {
        if (pConnector != null) {
            pConnector.close();
        }
    }

    /**
     * Override this if a special environment setup is required for JSR-160 connection
     *
     * @param pTargetConfig the target configuration as obtained from the request
     * @return the prepared environment
     */
    protected Map<String, Object> prepareEnv(Map<String, String> pTargetConfig) {
        if (pTargetConfig == null || pTargetConfig.isEmpty()) {
            return null;
        }
        Map<String, Object> ret = new HashMap<>(pTargetConfig);
        String user = (String) ret.remove("user");
        String password = (String) ret.remove("password");
        if (user != null && password != null) {
            ret.put(Context.SECURITY_PRINCIPAL, user);
            ret.put(Context.SECURITY_CREDENTIALS, password);
            ret.put("jmx.remote.credentials", new String[]{user, password});
        }
        // Prevents error "java.rmi.ConnectIOException: non-JRMP server at remote endpoint"
        if (System.getProperties().containsKey("javax.net.ssl.trustStore")) {
            ret.put("com.sun.jndi.rmi.factory.socket", new SslRMIClientSocketFactory());
        }
        return ret;
    }

    public String getProvider() {
        return "proxy";
    }

    /**
     * {@inheritDoc}
     */
    public void destroy() throws JMException {
        commandHandlerManager.destroy();
    }

    // Whether a given JMX Service URL is acceptable
    private boolean acceptTargetUrl(String urlS) {
        // Whitelist has precedence. Only patterns on the white list are allowed
        if (allowList != null) {
            return checkPattern(allowList, urlS, true);
        }

        // Then blacklist: Everything on this list is forbidden
        if (denyList != null) {
            return checkPattern(denyList, urlS, false);
        }

        // If no list is configured, then everything is allowed
        return true;
    }

    private boolean checkPattern(Set<String> patterns, String urlS, boolean isPositive) {
        for (String pattern : patterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(urlS).matches()) {
                return isPositive;
            }
        }
        return !isPositive;
    }

    private Set<String> extractAllowList(JolokiaContext pContext) {
        return extractFrom(
            System.getenv(ConfigKey.JSR160_PROXY_ALLOWED_TARGETS.asEnvVariable()),
            System.getProperty(ConfigKey.JSR160_PROXY_ALLOWED_TARGETS.asSystemProperty()),
            pContext != null ? pContext.getConfig(ConfigKey.JSR160_PROXY_ALLOWED_TARGETS) : null
        );
    }

    private Set<String> extractDenyList(JolokiaContext pContext) {
        // Bad, bad ....
        return Collections.singleton("service:jmx:rmi:///jndi/ldap:.*");
    }

    private Set<String> extractFrom(String... pPaths) {
        Set<String> ret = new HashSet<>();
        for (String path : pPaths) {
            if (path != null) {
                ret.addAll(readPatterns(path));
            }
        }
        return !ret.isEmpty() ? ret : null;
    }

    private List<? extends String> readPatterns(String pPath) {
        List<String> ret = new ArrayList<>();
        Pattern commentPattern = Pattern.compile("^\\s*#.*$");
        try (BufferedReader reader = new BufferedReader(new FileReader(pPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().startsWith("#")) {
                    ret.add(line);
                }
            }
            return ret;
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(String.format("No such pattern file %s", pPath));
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error while reading pattern file %s: %s", pPath, e.getMessage()));
        }
    }

}
