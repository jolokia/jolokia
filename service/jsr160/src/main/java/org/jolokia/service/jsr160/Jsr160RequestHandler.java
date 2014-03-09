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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.management.*;
import javax.management.remote.*;
import javax.naming.Context;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
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
        commandHandlerManager = new CommandHandlerManager(pContext,getRealm());
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
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        CommandHandler<R> handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());
        JMXConnector connector = getConnector(pJmxReq);
        try {
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
    private JMXConnector getConnector(JolokiaRequest pJmxReq) throws IOException {
        ProxyTargetConfig targetConfig = new ProxyTargetConfig((Map<String, String>) pJmxReq.getOption("target"));
        String urlS = targetConfig.getUrl();
        JMXServiceURL url = new JMXServiceURL(urlS);
        Map<String,Object> env = prepareEnv(targetConfig.getEnv());
        JMXConnector ret = JMXConnectorFactory.newJMXConnector(url,env);
        ret.connect();
        return ret;
    }

    private void releaseConnector(JMXConnector pConnector) throws IOException {
        pConnector.close();
    }

    /**
     * Override this if a special environment setup is required for JSR-160 connection
     *
     * @param pTargetConfig the target configuration as obtained from the request
     * @return the prepared environment
     */
    protected Map<String,Object> prepareEnv(Map<String, String> pTargetConfig) {
        if (pTargetConfig == null || pTargetConfig.size() == 0) {
            return null;
        }
        Map<String,Object> ret = new HashMap<String, Object>(pTargetConfig);
        String user = (String) ret.remove("user");
        String password  = (String) ret.remove("password");
        if (user != null && password != null) {
            ret.put(Context.SECURITY_PRINCIPAL, user);
            ret.put(Context.SECURITY_CREDENTIALS, password);
            ret.put("jmx.remote.credentials",new String[] { user, password });
        }
        return ret;
    }

    /**
     * The request can be handled when a target configuration is given. The realm name space is optional
     * here for backwards compatibility.
     *
     * {@inheritDoc}
     */
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return pJolokiaRequest.getOption("target") != null;
    }

    public String getRealm() {
        return "proxy";
    }

    public Object getRuntimeInfo() {
        return null;
    }

    /** {@inheritDoc} */
    public void destroy() throws JMException {
        commandHandlerManager.destroy();
    }
}
