package org.jolokia.service.jsr160;

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

import javax.management.*;
import javax.management.remote.*;
import javax.naming.Context;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.util.jmx.SingleMBeanServerAccess;
import org.jolokia.service.jmx.api.CommandHandler;
import org.jolokia.service.jmx.api.CommandHandlerManager;

/**
 * Dispatcher for calling JSR-160 connectors
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class Jsr160RequestHandler extends AbstractRequestHandler {

    // request handler for specific request types
    private CommandHandlerManager commandHandlerManager;

    // White and blacklist for patterns to match the JMX Service URL against
    private Set<String> whiteList;
    private Set<String> blackList;

    public static final String ALLOWED_TARGETS_SYSPROP = "org.jolokia.jsr160ProxyAllowedTargets";
    public static final String ALLOWED_TARGETS_ENV = "JOLOKIA_JSR160_PROXY_ALLOWED_TARGETS";

    /**
     * Create this request handler as service
     *
     * @param pOrder service order as given during construction.
     */
    public Jsr160RequestHandler(int pOrder) {
        super("proxy",pOrder);
    }

    /**
     * Initialization
     *
     * @param pContext the jolokia context
     */
    public void init(JolokiaContext pContext) {
        commandHandlerManager = new CommandHandlerManager(pContext, getProvider());
        whiteList = extractWhiteList(pContext);
        blackList = extractBlackList(pContext);
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
    public <R extends JolokiaRequest> Object handleRequest(R pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException, EmptyResponseException {

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

        Map<String,Object> env = prepareEnv(targetConfig.getEnv());
        return JMXConnectorFactory.newJMXConnector(url,env);
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
    protected Map<String,Object> prepareEnv(Map<String, String> pTargetConfig) {
        if (pTargetConfig == null || pTargetConfig.isEmpty()) {
            return null;
        }
        Map<String,Object> ret = new HashMap<>(pTargetConfig);
        String user = (String) ret.remove("user");
        String password  = (String) ret.remove("password");
        if (user != null && password != null) {
            ret.put(Context.SECURITY_PRINCIPAL, user);
            ret.put(Context.SECURITY_CREDENTIALS, password);
            ret.put("jmx.remote.credentials",new String[] { user, password });
        }
        // Prevents error "java.rmi.ConnectIOException: non-JRMP server at remote endpoint"
        if (System.getProperties().containsKey("javax.net.ssl.trustStore")) {
            ret.put("com.sun.jndi.rmi.factory.socket", new SslRMIClientSocketFactory());
        }
        return ret;
    }

    /**
     * The request can be handled when a target configuration is given. The provider name space is optional
     * here for backwards compatibility.
     *
     * {@inheritDoc}
     */
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return pJolokiaRequest.getOption("target") != null;
    }

    public String getProvider() {
        return "proxy";
    }

    /** {@inheritDoc} */
    public void destroy() throws JMException {
        commandHandlerManager.destroy();
    }

    // Whether a given JMX Service URL is acceptable
    private boolean acceptTargetUrl(String urlS) {
        // Whitelist has precedence. Only patterns on the white list are allowed
        if (whiteList != null) {
            return checkPattern(whiteList, urlS, true);
        }

        // Then blacklist: Everything on this list is forbidden
        if (blackList != null) {
            return checkPattern(blackList, urlS, false);
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

    private Set<String> extractWhiteList(JolokiaContext pContext) {
        return extractFrom(pContext != null ? pContext.getConfig(ConfigKey.JSR160_PROXY_ALLOWED_TARGETS) : null,
                           System.getProperty(ALLOWED_TARGETS_SYSPROP),
                           System.getenv(ALLOWED_TARGETS_ENV));
    }

    private Set<String> extractFrom(String ... paths) {
        Set<String> ret = new HashSet<>();
        for (String path : paths) {
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
            String line = reader.readLine();
            while (line != null) {
                if (!commentPattern.matcher(line).matches()) {
                    ret.add(line);
                }
                line = reader.readLine();
            }
            return ret;
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(String.format("No such pattern file %s", pPath));
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error while reading pattern file %s: %s", pPath, e.getMessage()));
        }
    }

    private Set<String> extractBlackList(JolokiaContext pContext) {
        // Bad, bad ....
        return Collections.singleton("service:jmx:rmi:///jndi/ldap:.*");
    }
}
