package org.jolokia.backend;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import javax.management.*;

import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.handler.JsonRequestHandler;
import org.jolokia.handler.RequestHandlerManager;
import org.jolokia.history.HistoryStore;
import org.jolokia.mbean.Config;
import org.jolokia.request.JmxRequest;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.DebugStore;
import org.jolokia.util.LogHandler;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestDispatcher implements RequestDispatcher {

    // Handler for finding and merging the various MBeanHandler
    private MBeanServerHandler mBeanServerHandler;

    private RequestHandlerManager requestHandlerManager;

    // An (optional) qualifier for registering MBeans.
    private String qualifier;

    /**
     * Create a new local dispatcher which accesses local MBeans.
     *
     * @param pConverters object/string converters
     * @param pRestrictor restrictor which checks the access for various operations
     * @param pQualifier optional qualifier for registering own MBean to allow for multiple J4P instances in the VM
     * @param pLogHandler local handler used for logging out errors and warnings
     */
    public LocalRequestDispatcher(Converters pConverters, Restrictor pRestrictor, String pQualifier, LogHandler pLogHandler) {
        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        mBeanServerHandler = new MBeanServerHandler(pQualifier,pLogHandler);
        qualifier = pQualifier;

        // Request handling manager 
        requestHandlerManager =
                new RequestHandlerManager(pConverters,mBeanServerHandler.getServerHandle(),pRestrictor);
    }

    // Can handle any request
    /** {@inheritDoc} */
    public boolean canHandle(JmxRequest pJmxRequest) {
        return true;
    }

    /** {@inheritDoc} */
    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }

    /** {@inheritDoc} */
    public Object dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxReq.getType());
        return mBeanServerHandler.dispatchRequest(handler, pJmxReq);
    }

    /**
     * Initialise this reques dispatcher, which will register a {@link org.jolokia.mbean.ConfigMBean} for easy external
     * access to the {@link HistoryStore} and {@link DebugStore}.
     *
     * @param pHistoryStore history store to be managed from within an MBean
     * @param pDebugStore managed debug store
     * @throws MalformedObjectNameException if our MBean's name is wrong (which cannot happen)
     * @throws MBeanRegistrationException if registration fails
     * @throws InstanceAlreadyExistsException if a config MBean is already present
     * @throws NotCompliantMBeanException if we have a non compliant MBean (cannot happen, too)
     */
    public void init(HistoryStore pHistoryStore, DebugStore pDebugStore)
            throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        mBeanServerHandler.init();

        // Register the Config MBean
        String oName = createObjectNameWithQualifier(Config.OBJECT_NAME);
        Config config = new Config(pHistoryStore,pDebugStore,oName);
        mBeanServerHandler.registerMBean(config,oName);

        // Register another Config MBean (which dispatched to the stores anyway) for access by
        // jmx4perl version < 0.80
        String legacyOName = createObjectNameWithQualifier(Config.LEGACY_OBJECT_NAME);
        Config legacyConfig = new Config(pHistoryStore,pDebugStore,legacyOName);
        mBeanServerHandler.registerMBean(legacyConfig,legacyOName);
    }

    /**
     * Unregister the config MBean
     *
     * @throws JMException is unregistration fails
     */
    public void destroy() throws JMException {
        mBeanServerHandler.unregisterMBeans();
    }

    /**
     * Get information about the current server
     * @return the server information
     */
    public ServerHandle getServerInfo() {
        return mBeanServerHandler.getServerHandle();
    }

    private String createObjectNameWithQualifier(String pOName) {
        return pOName + (qualifier != null ? "," + qualifier : "");
    }
}
