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

import org.jolokia.service.jmx.handler.CommandHandler;
import org.jolokia.service.jmx.handler.CommandHandlerManager;
import org.jolokia.core.backend.NotChangedException;
import org.jolokia.core.service.ServerHandle;
import org.jolokia.core.service.request.AbstractRequestHandler;
import org.jolokia.core.service.request.RequestHandler;
import org.jolokia.core.config.ConfigKey;
import org.jolokia.core.util.jmx.LocalMBeanServerExecutor;
import org.jolokia.core.request.JolokiaObjectNameRequest;
import org.jolokia.core.request.JolokiaRequest;
import org.jolokia.core.service.JolokiaContext;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestHandler extends AbstractRequestHandler implements RequestHandler {

    private CommandHandlerManager commandHandlerManager;
    private JolokiaContext jolokiaContext;

    // Initialize used for late initialization
    // ("volatile: because we use double-checked locking later on
    // --> http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)
    private volatile Initializer initializer;

    private LocalMBeanServerExecutor executor;

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

        // where Detectors have to be initialized.
        if (Boolean.parseBoolean(pCtx.getConfig(ConfigKey.LAZY_SERVER_DETECTION))) {
            initializer = new Initializer();
        } else {
            new Initializer().init();
            initializer = null;
        }
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
        lazyInitIfNeeded();

        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());
        jolokiaContext.getServerHandle().preDispatch(executor,pJmxReq);
        if (handler.handleAllServersAtOnce(pJmxReq)) {
            try {
                return handler.handleRequest(executor, pJmxReq, pPreviousResult);
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
        executor.destroy();
    }

    /** {@inheritDoc} */
    @Override
    public Object getRuntimeInfo() {
        ServerHandle handle = jolokiaContext.getServerHandle();
        return handle.getExtraInfo(executor);
    }

    // =====================================================================================================

    // Handle a single request
    private <R extends JolokiaRequest> Object handleRequest(CommandHandler<R> pRequestHandler, R pJmxReq)
            throws MBeanException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, NotChangedException {
        AttributeNotFoundException attrException = null;
        InstanceNotFoundException objNotFoundException = null;

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

    // Run initialized if not already done
    private void lazyInitIfNeeded() {
        if (initializer != null) {
            synchronized (this) {
                if (initializer != null) {
                    initializer.init();
                    initializer = null;
                }
            }
        }
    }

    // Initialized used for late initialisation as it is required for the agent when used
    // as startup options
    private final class Initializer {
        void init() {
            ServerHandleFinder finder = new ServerHandleFinder(jolokiaContext);
            executor = new LocalMBeanServerExecutor(finder.getExtraMBeanServers());
            ServerHandle handle = finder.detectServerHandle(executor);
            jolokiaContext.setServerHandle(handle);
        }
    }

}
