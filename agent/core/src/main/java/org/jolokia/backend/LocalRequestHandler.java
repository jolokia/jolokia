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

import javax.management.*;

import org.jolokia.backend.dispatcher.*;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.detector.ServerHandle;
import org.jolokia.handler.CommandHandler;
import org.jolokia.handler.CommandHandlerManager;
import org.jolokia.request.JolokiaObjectNameRequest;
import org.jolokia.request.JolokiaRequest;
import org.jolokia.service.JolokiaContext;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestHandler extends AbstractRequestHandler implements RequestHandler {

    private MBeanServerExecutorLocal mBeanServerManager;
    private CommandHandlerManager commandHandlerManager;
    private JolokiaContext jolokiaContext;

    // Initialize used for late initialization
    // ("volatile: because we use double-checked locking later on
    // --> http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)
    private volatile Initializer initializer;

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
        mBeanServerManager = new MBeanServerExecutorLocal();
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
    public boolean useReturnValueWithPath(JolokiaRequest pJolokiaRequest) {
        CommandHandler handler = commandHandlerManager.getCommandHandler(pJolokiaRequest.getType());
        return handler.useReturnValueWithPath();
    }

    /** {@inheritDoc} */
    public Object handleRequest(JolokiaRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException {
        lazyInitIfNeeded();

        CommandHandler handler = commandHandlerManager.getCommandHandler(pJmxReq.getType());
        jolokiaContext.getServerHandle().preDispatch(mBeanServerManager, pJmxReq);
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

    /** {@inheritDoc} */
    @Override
    public Object getRuntimeInfo() {
        ServerHandle handle = jolokiaContext.getServerHandle();
        return handle.getExtraInfo(mBeanServerManager);
    }

// =====================================================================================================

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
            mBeanServerManager.init(finder.getExtraMBeanServers());
              ServerHandle handle = finder.detectServerHandle(mBeanServerManager);
            jolokiaContext.setServerHandle(handle);
        }
    }


}
