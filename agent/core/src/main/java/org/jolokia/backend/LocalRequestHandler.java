package org.jolokia.backend;

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

import javax.management.*;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.handler.OperationHandler;
import org.jolokia.handler.OperationHandlerManager;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaContext;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestHandler implements RequestHandler {

    // Jolokia context
    private final JolokiaContext ctx;

    private OperationHandlerManager operationHandlerManager;

    /**
     * Create a new local dispatcher which accesses local MBeans.
     *
     * @param pCtx context to use for this dispatcher
     */
    public LocalRequestHandler(JolokiaContext pCtx) {

        ctx = pCtx;

        // Request handling manager 
        operationHandlerManager =  new OperationHandlerManager(pCtx,true);
    }

    // Can handle any request
    /** {@inheritDoc} */
    public boolean canHandle(JmxRequest pJmxRequest) {
        return true;
    }

    /** {@inheritDoc} */
    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        OperationHandler handler = operationHandlerManager.getRequestHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }

    /** {@inheritDoc} */
    public Object dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException {
        OperationHandler handler = operationHandlerManager.getRequestHandler(pJmxReq.getType());
        return ctx.getMBeanServerHandler().dispatchRequest(handler, pJmxReq);
    }

    /**
     * Unregister the config MBean
     *
     * @throws JMException if unregistration fails
     */
    public void destroy() throws JMException {
        //mBeanServerHandler.destroy();
        operationHandlerManager.destroy();
    }
}
