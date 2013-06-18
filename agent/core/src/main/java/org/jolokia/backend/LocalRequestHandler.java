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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.management.*;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.detector.*;
import org.jolokia.handler.CommandHandler;
import org.jolokia.handler.CommandHandlerManager;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.AbstractJolokiaService;
import org.jolokia.service.JolokiaContext;
import org.jolokia.service.impl.JolokiaContextImpl;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestHandler extends AbstractJolokiaService implements RequestHandler {
    private MBeanServerExecutorLocal mBeanServerManager;
    private CommandHandlerManager commandHandlerManager;
    private ServerHandle serverHandle;

    /**
     * Create a new local dispatcher which accesses local MBeans.
     */
    public LocalRequestHandler(int pOrder) {
        super(RequestHandler.class,pOrder);
    }


    /** {@inheritDoc} */
    // This service must be initialized after the detectors, since detectors will be
    // looked up in this init
    public void init(JolokiaContext pCtx) {
        commandHandlerManager =  new CommandHandlerManager(pCtx,true);
        mBeanServerManager = new MBeanServerExecutorLocal();

        // Request handling manager
        // TODO: Introduce lazy detection for the Agent based, startup option
        List<ServerDetector> detectors = lookupDetectors(pCtx);
        // Lookup all MBeanServers. Needs to be done before the server handle detection
        // so that all available MBeanServer have been already collected
        mBeanServerManager.init(detectors);

        serverHandle = detectServerHandle(pCtx, detectors);
    }

    // Can handle any request
    /** {@inheritDoc} */
    public boolean canHandle(JmxRequest pJmxRequest) {
        return true;
    }

    /** {@inheritDoc} */
    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }

    /** {@inheritDoc} */
    public Object handleRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException {
        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());
        serverHandle.preDispatch(mBeanServerManager, pJmxReq);
        if (handler.handleAllServersAtOnce(pJmxReq)) {
            try {
                return handler.handleRequest(mBeanServerManager, pJmxReq);
            } catch (IOException e) {
                throw new IllegalStateException("Internal: IOException " + e + ". Shouldn't happen.",e);
            }
        } else {
            return mBeanServerManager.handleRequest(handler, pJmxReq);
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

    /**
     * Initialize the server handle and update the context.
     *
     * @param pCtx the jolokia context used for initializing the server handle
     * @param pDetectors all detectors-default known
     */
    private ServerHandle detectServerHandle(JolokiaContext pCtx, List<ServerDetector> pDetectors) {
        ServerHandle handle = detectServers(pDetectors, pCtx);
        handle.postDetect(mBeanServerManager, pCtx);
        handle.setJolokiaId(extractJolokiaId(pCtx));
        if (pCtx instanceof JolokiaContextImpl) {
            ((JolokiaContextImpl) pCtx).setServerHandle(handle);
        }
        return handle;
    }

    /**
     * Extract a unique Id for this agent
     *
     * @param pContext the jolokia context
     * @return the unique Jolokia ID
     */
    private String extractJolokiaId(JolokiaContext pContext) {
        String id = pContext.getConfig(ConfigKey.JOLOKIA_ID);
        if (id != null) {
            return id;
        }
        return Integer.toHexString(hashCode()) + "-unknown";
    }

    // Lookup all registered detectors-default + a default detector
    private List<ServerDetector> lookupDetectors(JolokiaContext pCtx) {
        List<ServerDetector> detectors = new ArrayList<ServerDetector>();
        detectors.addAll(pCtx.getServices(ServerDetector.class));
        // An detector at the end of the chain in order to get a default handle
        detectors.add(new FallbackServerDetector());
        return detectors;
    }

    // Detect the server by delegating it to a set of predefined detectors-default. These will be created
    // by a lookup mechanism, queried and thrown away after this method
    private ServerHandle detectServers(List<ServerDetector> pDetectors, JolokiaContext pCtx) {
        // Now detect the server
        for (ServerDetector detector : pDetectors) {
            try {
                ServerHandle info = detector.detect(mBeanServerManager);
                if (info != null) {
                    return info;
                }
            } catch (Exception exp) {
                // We are defensive here and wont stop the servlet because
                // there is a problem with the server detection. A error will be logged
                // nevertheless, though.
                pCtx.error("Error while using detector " + detector.getClass().getSimpleName() + ": " + exp,exp);
            }
        }
        return ServerHandle.NULL_SERVER_HANDLE;
    }

    // ==================================================================================
    // Fallback server detector which matches always

    private static class FallbackServerDetector extends AbstractServerDetector {
        public FallbackServerDetector() {
            super(10000);
        }

        /** {@inheritDoc}
         * @param pMBeanServerExecutor*/
        public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
            return ServerHandle.NULL_SERVER_HANDLE;
        }
    }
}
