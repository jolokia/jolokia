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

import org.jolokia.JmxRequest;
import org.jolokia.config.Config;
import org.jolokia.config.DebugStore;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.handler.*;
import org.jolokia.history.HistoryStore;

import javax.management.*;

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
     * @param objectToJsonConverter a serializer to JSON
     * @param stringToObjectConverter a de-serializer for arguments
     * @param restrictor restrictor which checks the access for various operations
     * @param pQualifier optional qualifier for registering own MBean to allow for multiple J4P instances in the VM
     */
    public LocalRequestDispatcher(ObjectToJsonConverter objectToJsonConverter,
                                  StringToObjectConverter stringToObjectConverter,
                                  Restrictor restrictor, String pQualifier) {
        requestHandlerManager = new RequestHandlerManager(objectToJsonConverter,stringToObjectConverter,restrictor);
        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        mBeanServerHandler = new MBeanServerHandler(pQualifier);
        qualifier = pQualifier;
    }



    // Can handle any request
    public boolean canHandle(JmxRequest pJmxRequest) {
        return true;
    }

    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }

    public Object dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxReq.getType());
        return mBeanServerHandler.dispatchRequest(handler, pJmxReq);
    }

    public void init(HistoryStore pHistoryStore, DebugStore pDebugStore)
            throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        mBeanServerHandler.init();

        // Register the Config MBean
        Config config = new Config(pHistoryStore,pDebugStore,qualifier,Config.OBJECT_NAME);
        mBeanServerHandler.registerMBean(config,config.getObjectName());

        // Register another Config MBean (which dispatched to the stores anyway) for access by
        // jmx4perl version < 0.80
        Config legacyConfig = new Config(pHistoryStore,pDebugStore,qualifier,Config.LEGACY_OBJECT_NAME);
        mBeanServerHandler.registerMBean(legacyConfig,legacyConfig.getObjectName());
    }

    public void destroy() throws JMException {
        mBeanServerHandler.unregisterMBeans();
    }
}
