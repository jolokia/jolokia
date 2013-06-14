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

/**
 * @author roland
 * @since 28.03.13
 */
public class JolokiaServiceManagerImpl implements JolokiaServiceManager {

    // Overall configuration
    private Configuration configuration;

    // Logger to use
    private LogHandler logHandler;

    // Restrictor to use
    private final Restrictor restrictor;

    // Whether this service manager is already initialized
    private boolean isInitialized;

    // All service factories used
    private Map<Class<? extends JolokiaService>,
            Set<JolokiaServiceFactory<? extends JolokiaService>>> dynamicServiceFactories;

    // Instantiated services, categorized by type and ordered;
    private Map<Class<? extends JolokiaService>,SortedSet<? extends JolokiaService>> staticServices;

    // Jolokia context connecting to this manager
    private JolokiaContextImpl jolokiaContext;

    // Server handler for registering MBeans
    private MBeanRegistry mBeanServerHandler;

    // Request dispatcher for executing operations
    private RequestDispatcher requestDispatcher;

    public JolokiaServiceManagerImpl(Configuration pConfig,LogHandler pLogHandler, Restrictor pRestrictor) {
        configuration = pConfig;
        logHandler = pLogHandler;
        restrictor = pRestrictor;
        isInitialized = false;
        dynamicServiceFactories = new HashMap<Class<? extends JolokiaService>, Set<JolokiaServiceFactory<? extends JolokiaService>>>();
        staticServices = new HashMap<Class<? extends JolokiaService>, SortedSet <? extends JolokiaService>>();
    }

    /**
     * Register a service which was instantiated by external means.
     * The lifecycle management goes over to this manager.
     *
     * @param pService service to register
     */
    public <T extends JolokiaService> void addService(T pService) {
        SortedSet<T> servicesOfType = (SortedSet<T>) staticServices.get(pService.getType());
        if (servicesOfType == null) {
            servicesOfType = new TreeSet<T>();
            staticServices.put(pService.getType(), servicesOfType);
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
    public <T extends JolokiaService> void addServiceFactory(JolokiaServiceFactory<T> pFactory) {
        Class<T> type = pFactory.getType();
        Set<JolokiaServiceFactory<? extends JolokiaService>> factories = dynamicServiceFactories.get(type);
        if (factories == null) {
            factories = new TreeSet<JolokiaServiceFactory<? extends JolokiaService>>();
            dynamicServiceFactories.put(type, factories);
        }
        factories.add(pFactory);
    }

    public <T extends JolokiaService> void addServices(JolokiaServiceCreator<T> pServiceCreator) {
        for (T service : pServiceCreator.getServices()) {
            addService(service);
        }
    }

    /**
     * Start up the service manager. All pre-instantiated services sh
     *
     * @return the created jolokia context
     */
    public synchronized JolokiaContext start() {
        if (!isInitialized) {

            // Create context and remember
            jolokiaContext = new JolokiaContextImpl(configuration, logHandler, restrictor);

            // Initialize all services
            for (Set<? extends JolokiaService> services : staticServices.values()) {
                for (JolokiaService service : services) {
                    service.init(jolokiaContext);
                }
            }

            // All dynamic service factories are initialized as well. The factory itmself is responsible
            // for initializing any new services coming in with the JolokiaContext.
            for (Set<JolokiaServiceFactory<? extends JolokiaService>> dFactories : dynamicServiceFactories.values()) {
                for (JolokiaServiceFactory factory : dFactories) {
                    factory.init(jolokiaContext);
                }
            }

            // Main initialization ....
            initMBeans(jolokiaContext);
            requestDispatcher = new RequestDispatcherImpl(this);

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
                logHandler.error("Cannot unregister own MBeans: " + e, e);
            }
            for (Set<JolokiaServiceFactory<? extends JolokiaService>> dFactories : dynamicServiceFactories.values()) {
                for (JolokiaServiceFactory factory : dFactories) {
                    factory.destroy();
                }
            }
            for (Set<? extends JolokiaService> services : staticServices.values()) {
                for (JolokiaService service : services) {
                    try {
                        service.destroy();
                    } catch (Exception e) {
                        logHandler.error("Error while stopping service " + service + " of type " + service.getType() + ": " + e,e);
                    }
                }
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


    private <T extends JolokiaService> T getMandatorySingletonService(Class<T> pType) {
        Set<T> services = getServices(pType);
        if (services == null || services.size() == 0) {
            throw new IllegalStateException("No service of type " + pType + " registered");
        } else if (services.size() > 1) {
            throw new IllegalStateException("More than one service of type " + pType + " registered");
        }
        return services.iterator().next();
    }

    /**
     * Get all services of a certain type currently registered. Static services
     * are returned directly, for dynamic services a lookup to the service factory is
     * performed.
     *
     * @param pType service type to fetch
     * @return list of services detected or an empty list
     */
    public <T extends JolokiaService> Set<T> getServices(Class<T> pType) {
        Set<JolokiaServiceFactory<? extends JolokiaService>> factories = dynamicServiceFactories.get(pType);
        Set<T> services = (Set<T>) staticServices.get(pType);
        if (services == null) {
            services = new TreeSet<T>();
        }
        if (factories == null) {
            return services;
        } else {
            SortedSet<T> ret = new TreeSet<T>();
            ret.addAll(services);
            for (JolokiaServiceFactory<? extends JolokiaService> factory : factories) {
                ret.addAll((Set<? extends T>) factory.getServices());
            }
            return ret;
        }
    }
}
