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
package org.jolokia.service.jmx;

import java.io.IOException;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaObjectNameRequest;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.api.CommandHandler;
import org.jolokia.service.jmx.api.CommandHandlerManager;

/**
 * The <em>main</em> Jolokia {@link org.jolokia.server.core.service.request.RequestHandler}, which
 * invokes an operation on one or more local {@link javax.management.MBeanServer MBean servers} through
 * a {@link MBeanServerAccess} interface.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestHandler extends AbstractRequestHandler {

    /** Each {@link JolokiaRequest} is handled by a dedicated {@link CommandHandler}. */
    private CommandHandlerManager commandHandlerManager;

    /** This is how this request handler accesses managed {@link MBeanServer MBean servers}. */
    private MBeanServerAccess jmxAccess;

    /**
     * Create a new <em>local</em> request handler which accesses local MBeans.
     */
    public LocalRequestHandler(int pOrder) {
        super("jmx", pOrder);
    }

    // This service must be initialized after the detectors, since detectors will be looked up in this init
    @Override
    public void init(JolokiaContext pCtx) {
        commandHandlerManager =  new CommandHandlerManager(pCtx);
        jmxAccess = pCtx.getMBeanServerAccess();
    }

    // Can handle all request starting with "jmx" or with a null provider
    @Override
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        if (pJolokiaRequest instanceof JolokiaObjectNameRequest objectNameRequest) {
            return objectNameRequest.getProvider() == null || checkProvider(objectNameRequest);
        } else {
            return true;
        }
    }

    @Override
    public <R extends JolokiaRequest> Object handleRequest(R pJmxReq, Object pPreviousResult)
            throws IOException, JMException, JMRuntimeException, NotChangedException, BadRequestException, EmptyResponseException {
        // in theory we should not throw IOException, but we access the MBeanServers using
        // javax.management.MBeanServerConnection interface, so we propagate the exception

        CommandHandler<R> handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());

        if (handler.handleAllServersAtOnce(pJmxReq)) {
            // handler itself will iterate over the available MBeanServer(Connection)s
            return handler.handleAllServerRequest(jmxAccess, pJmxReq, pPreviousResult);
        } else {
            // we're doing the iteration and return a value from the first MBeanServerConnection that
            // doesn't throw an exception
            // special case is (legacy reasons, not breaking existing behavior) that InstanceNotFoundException
            // and AttributeNotFoundException are not breaking the loop
            JMException thrownIfNoResult = null;
            for (MBeanServerConnection server : jmxAccess.getMBeanServers()) {
                try {
                    // if there's a result, do not check other servers
                    return handler.handleSingleServerRequest(server, pJmxReq);
                } catch (InstanceNotFoundException | AttributeNotFoundException ex) {
                    thrownIfNoResult = ex;
                }
            }

            // if there's no result from any connection, throw what we have
            if (thrownIfNoResult != null) {
                throw thrownIfNoResult;
            }

            // no way we get here (unless there's no MBeanServer(Connection) at all)
            throw new IllegalStateException("No available MBeanServerConnection to send " + pJmxReq);
        }
    }

    /**
     * Unregister the config MBean
     *
     * @throws JMException if unregistration fails
     */
    @Override
    public void destroy() throws JMException {
        commandHandlerManager.destroy();
    }

}
