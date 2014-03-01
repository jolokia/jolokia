package org.jolokia.service.jmx;

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

import javax.management.*;

import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.JolokiaContext;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.handler.CommandHandler;
import org.jolokia.service.jmx.handler.CommandHandlerManager;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestHandler extends AbstractRequestHandler implements RequestHandler {

    private CommandHandlerManager commandHandlerManager;

    // Context to use
    private JolokiaContext jolokiaContext;

    /**
     * Create a new local dispatcher which accesses local MBeans.
     */
    public LocalRequestHandler(int pOrder) {
        super("jmx",pOrder);
    }

    /** {@inheritDoc} */
    // This service must be initialized after the detectors, since detectors will be
    // looked up in this init
    public void init(JolokiaContext pCtx) {
        commandHandlerManager =  new CommandHandlerManager(pCtx,true);
        jolokiaContext = pCtx;
    }

    // Can handle all request starting with "jmx" or with a null realm
    /** {@inheritDoc} */
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        if (pJolokiaRequest instanceof JolokiaObjectNameRequest) {
            JolokiaObjectNameRequest oReq = (JolokiaObjectNameRequest) pJolokiaRequest;
            return oReq.getRealm() == null || checkRealm(oReq);
        } else {
            return true;
        }
    }

    /** {@inheritDoc} */
    public Object handleRequest(JolokiaRequest pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException {

        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());

        if (handler.handleAllServersAtOnce(pJmxReq)) {
            try {
                return handler.handleRequest(jolokiaContext.getMBeanServerAccess(), pJmxReq, pPreviousResult);
            } catch (IOException e) {
                throw new IllegalStateException("Internal: IOException " + e + ". Shouldn't happen.",e);
            }
        } else {
            return handleRequest(handler, pJmxReq);
        }
    }

    /**
     * Unregister the config MBean
     *
     * @throws JMException if unregistration fails
     */
    public void destroy() throws JMException {
        commandHandlerManager.destroy();
    }

    // =====================================================================================================

    // Handle a single request
    private <R extends JolokiaRequest> Object handleRequest(CommandHandler<R> pRequestHandler, R pJmxReq)
            throws MBeanException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, NotChangedException {
        AttributeNotFoundException attrException = null;
        InstanceNotFoundException objNotFoundException = null;

        MBeanServerAccess executor = jolokiaContext.getMBeanServerAccess();
        for (MBeanServerConnection conn : executor.getMBeanServers()) {
            try {
                return pRequestHandler.handleRequest(conn, pJmxReq);
            } catch (InstanceNotFoundException exp) {
                // Remember exceptions for later use
                objNotFoundException = exp;
            } catch (AttributeNotFoundException exp) {
                attrException = exp;
            } catch (IOException exp) {
                throw new IllegalStateException("I/O Error while dispatching",exp);
            }
        }
        if (attrException != null) {
            throw attrException;
        }
        // Must be there, otherwise we would not have left the loop
        throw objNotFoundException;
    }
}
