package org.jolokia.jsr160;

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

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.handler.CommandHandler;
import org.jolokia.handler.CommandHandlerManager;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.ProxyTargetConfig;
import org.jolokia.service.JolokiaContext;

/**
 * Dispatcher for calling JSR-160 connectors
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class Jsr160RequestHandler implements RequestHandler {

    // request handler for specific request types
    private CommandHandlerManager commandHandlerManager;

    /**
     * Constructor
     *
     * @param pContext the jolokia context
     */
    public Jsr160RequestHandler(JolokiaContext pContext) {
        commandHandlerManager = new CommandHandlerManager(pContext,false);
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
    public Object dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {

        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());
        JMXConnector connector = getConnector(pJmxReq);
        try {
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            if (handler.handleAllServersAtOnce(pJmxReq)) {
                // There is no way to get remotely all MBeanServers ...
                MBeanServerExecutor manager = new MBeanServerExecutorRemote(connection);
                return handler.handleRequest(manager,pJmxReq);
            } else {
                return handler.handleRequest(connection,pJmxReq);
            }
        } finally {
            releaseConnector(connector);
        }
    }

    // TODO: Add connector to a pool and release it on demand. For now, simply close it.
    private JMXConnector getConnector(JmxRequest pJmxReq) throws IOException {
        ProxyTargetConfig targetConfig = pJmxReq.getTargetConfig();
        if (targetConfig == null) {
            throw new IllegalArgumentException("No proxy configuration in request " + pJmxReq);
        }
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
     * The request can be handled when a target configuration is given.
     *
     * {@inheritDoc}
     */
    public boolean canHandle(JmxRequest pJmxRequest) {
        return pJmxRequest.getTargetConfig() != null;
    }

    /** {@inheritDoc} */
    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }

    /** {@inheritDoc} */
    public void destroy() throws JMException {
        commandHandlerManager.destroy();
    }
}
