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

package org.jolokia.service.impl;

import java.util.*;

import javax.management.*;

import org.jolokia.backend.MBeanRegistry;
import org.jolokia.backend.dispatcher.RequestDispatcher;
import org.jolokia.backend.dispatcher.RequestDispatcherImpl;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.history.History;
import org.jolokia.history.HistoryStore;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.*;
import org.jolokia.util.LogHandler;

import static org.jolokia.service.JolokiaService.ServiceType;

/**
 * @author roland
 * @since 28.03.13
 */
public class JolokiaServiceManagerImpl implements JolokiaServiceManager {

    // Overall configuration
    private Configuration configuration;

    // Logger to use
    private LogHandler logHandler;

    protected LocalServiceFactory localServiceFactory;

    // Whether this service manager is already initialized
    private boolean isInitialized;

    // All service factories used
    private Set<JolokiaServiceFactory> serviceFactories;

    // Instantiated services, categorized by type and ordered;
    private Map<ServiceType,SortedSet<JolokiaService>> services;

    // Jolokia context connecting to this manager
    private JolokiaContextImpl jolokiaContext;

    // Server handler for registering MBeans
    private MBeanRegistry mBeanServerHandler;

    // Request dispatcher for executing operations
    private RequestDispatcher requestDispatcher;

    public JolokiaServiceManagerImpl(Configuration pConfig,LogHandler pLogHandler) {
        configuration = pConfig;
        logHandler = pLogHandler;
        isInitialized = false;
        serviceFactories = new HashSet<JolokiaServiceFactory>();
        services = new HashMap<ServiceType,SortedSet<JolokiaService>>();
    }

    /**
     * Register a service which was instantiated by external means.
     * The lifecycle management goes over to this manager.
     *
     * @param pService service to register
     */
    public void addService(JolokiaService pService) {
        SortedSet<JolokiaService> servicesOfType = services.get(pService.getType());
        if (servicesOfType == null) {
            servicesOfType = new TreeSet<JolokiaService>(new ServiceComparator());
            services.put(pService.getType(),servicesOfType);
        }
        servicesOfType.add(pService);
    }

    /**
     * Remove a service when it goes out of scope
     *
     * @param pService service to remove
     */
    public void removeService(JolokiaService pService) {
    }

    /**
     * Add a service factory for initially looking up services e.g.
     * by class path scanning
     *
     * @param pFactory
     */
    public void addServiceFactory(JolokiaServiceFactory pFactory) {
        serviceFactories.add(pFactory);
    }

    /**
     * Start up the service manager. All pre-instantiated services sh
     *
     * @return the created jolokia context
     */
    public synchronized JolokiaContext start() {
        if (!isInitialized) {

            // Lookup all services by the given service factories
            // The factories add the services on their own.
            for (JolokiaServiceFactory factory : serviceFactories) {
                factory.init(this);
            }
            // Call init on all services

            // Only one restrictor is allowed and must be present.
            Restrictor restrictor =
                    (Restrictor) getMandatorySingletonService(ServiceType.RESTRICTOR);

            // Create context and remember
            jolokiaContext = new JolokiaContextImpl(configuration, logHandler, restrictor);

            initMBeans(jolokiaContext);

            // Initialize the request dispatcher
            requestDispatcher = new RequestDispatcherImpl(jolokiaContext);

            isInitialized = true;
        }
        return jolokiaContext;
    }

    /**
     * Stop all services and destroy the managed context
     *
     */
    public synchronized void stop() {
        if (isInitialized) {
            try {
                mBeanServerHandler.destroy();
            } catch (JMException e) {
                getLogHandler().error("Cannot unregister own MBeans: " + e,e);
            }
            for (JolokiaServiceFactory factory : serviceFactories) {
                factory.destroy();
            }
            try {
                requestDispatcher.destroy();
            } catch (JMException e) {
                getLogHandler().error("Cannot stop the request dispatcher: " + e,e);
            }
        }
        isInitialized = false;
    }

    private void initMBeans(JolokiaContextImpl pCtx) {
        mBeanServerHandler = new MBeanRegistry(pCtx);
        initHistoryStore(pCtx);

        // TODO: MBeanServer Infos MBean which exports org.jolokia.backend.MBeanServerExecutorLocal.getServersInfo()
        // Under "jolokia:type=ServerHandler"
    }

    private void initHistoryStore(JolokiaContextImpl pCtx) {
        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        /// TODO: Initialisation of Detectors must be done lazily for the JVM agent here ...

        int maxEntries;
        try {
            maxEntries = Integer.parseInt(pCtx.getConfig(ConfigKey.HISTORY_MAX_ENTRIES));
        } catch (NumberFormatException exp) {
            maxEntries = Integer.parseInt(ConfigKey.HISTORY_MAX_ENTRIES.getDefaultValue());
        }
        HistoryStore historyStore = new HistoryStore(maxEntries);
        try {

            // Register the Config MBean
            String qualifier = pCtx.getConfig(ConfigKey.MBEAN_QUALIFIER);
            String oName = History.OBJECT_NAME + (qualifier != null ? "," + qualifier : "");

            History history = new History(historyStore,oName);
            mBeanServerHandler.registerMBean(history, oName);
        } catch (InstanceAlreadyExistsException exp) {
            // That's ok, we are reusing it.
        } catch (NotCompliantMBeanException e) {
            pCtx.error("Error registering config MBean: " + e, e);
        } catch (MalformedObjectNameException e) {
            pCtx.error("Invalid name for config MBean: " + e, e);
        }
        //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
        //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
    }

    public RequestDispatcher getRequestDispatcher() {
        if (requestDispatcher == null) {
            throw new IllegalStateException("Service Manager not yet started, please call start() before");
        }
        return requestDispatcher;
    }


    public LogHandler getLogHandler() {
        return logHandler;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    private JolokiaService getMandatorySingletonService(ServiceType pType) {
        Set<JolokiaService> services = getServices(pType);
        if (services == null || services.size() == 0) {
            throw new IllegalStateException("No service of type " + pType + " registered");
        } else if (services.size() > 1) {
            throw new IllegalStateException("More than one service of type " + pType + " registered");
        }
        return services.iterator().next();
    }

    /**
     * Get all services of a certain type currently registered
     *
     * @param pType service type to fetch
     * @return list of services detected or an empty list
     */
    Set<JolokiaService> getServices(ServiceType pType) {
        return services.get(pType);
    }

    // Comparator used for sorting services
    private static class  ServiceComparator implements Comparator<JolokiaService> {
        public int compare(JolokiaService o1, JolokiaService o2) {
            return o1.getOrder() - o2.getOrder();
        }
    }
}
