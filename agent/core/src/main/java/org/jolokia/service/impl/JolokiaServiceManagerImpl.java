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
import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.config.Configuration;
import org.jolokia.detector.ServerDetector;
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

    // Order in which services get initialized
    private static final Class[] SERVICE_TYPE_ORDER =
            new Class[] { ServerDetector.class, RequestHandler.class};

    // All service factories used
    private List<JolokiaServiceLookup> serviceLookups;

    // Instantiated services, categorized by type and ordered;
    private Map<Class<? extends JolokiaService>,SortedSet<? extends JolokiaService>> staticServices;

    // The lowest order service registered
    private Map<Class<? extends JolokiaService>, JolokiaService> staticLowServices;

    // Jolokia context connecting to this manager
    private JolokiaContextImpl jolokiaContext;

    // MBean registry for holding MBeans
    private MBeanRegistry mbeanRegistry;

    /**
     * Create the implementation of a service manager
     *
     * @param pConfig configuration to use
     * @param pLogHandler the logger
     * @param pRestrictor restrictor to apply
     */
    public JolokiaServiceManagerImpl(Configuration pConfig,LogHandler pLogHandler, Restrictor pRestrictor) {
        configuration = pConfig;
        logHandler = pLogHandler;
        restrictor = pRestrictor;
        isInitialized = false;
        serviceLookups = new ArrayList<JolokiaServiceLookup>();
        staticServices = new HashMap<Class<? extends JolokiaService>, SortedSet <? extends JolokiaService>>();
        staticLowServices = new HashMap<Class<? extends JolokiaService>, JolokiaService>();

        // The version request handler must be always present and always be first
        addService(new VersionRequestHandler());
    }


    /** {@inheritDoc} */
    public Configuration getConfiguration() {
        return configuration;
    }

    /** {@inheritDoc} */
    public LogHandler getLogHandler() {
        return logHandler;
    }

    /** {@inheritDoc} */
    public Restrictor getRestrictor() {
        return restrictor;
    }

    /** {@inheritDoc} */
    public synchronized void addService(JolokiaService pService) {
        Class<? extends JolokiaService> type = pService.getType();
        SortedSet<JolokiaService> servicesOfType = (SortedSet<JolokiaService>) staticServices.get(type);
        if (servicesOfType == null) {
            servicesOfType = new TreeSet<JolokiaService>();
            staticServices.put(type, servicesOfType);
        }
        servicesOfType.add(pService);
        JolokiaService pLowService = staticLowServices.get(type);
        if (pLowService == null || pLowService.getOrder() > pService.getOrder()) {
            staticLowServices.put(type,pService);
        }
    }

    /** {@inheritDoc} */
    public void addServiceLookup(JolokiaServiceLookup pLookup) {
        serviceLookups.add(pLookup);
    }

    /** {@inheritDoc} */
    public void addServices(JolokiaServiceCreator pServiceCreator) {
        for (JolokiaService service : pServiceCreator.getServices()) {
            addService(service);
        }
    }

    /** {@inheritDoc} */
    public synchronized JolokiaContext start() {
        if (!isInitialized) {

            // Create context and remember
            jolokiaContext = new JolokiaContextImpl(this);

            // Create the MBean registry
            mbeanRegistry = new MBeanRegistry();

            // Initialize all services in the proper order
            List<Class<? extends JolokiaService>> serviceTypes = getServiceTypes();
            for (Class<? extends JolokiaService> serviceType : serviceTypes) {
                // Initialize services
                Set<? extends JolokiaService> services = staticServices.get(serviceType);
                if (services != null) {
                    for (JolokiaService service : services) {
                        service.init(jolokiaContext);
                    }
                }
            }

            // All dynamic service factories are initialized as well. The factory itself is responsible
            // for initializing any new services coming in with the JolokiaContext
            for (JolokiaServiceLookup lookup : serviceLookups) {
                lookup.init(jolokiaContext);
            }

            isInitialized = true;
        }
        return jolokiaContext;
    }

    /** {@inheritDoc} */
    public synchronized void stop() {
        if (isInitialized) {
            try {
                mbeanRegistry.destroy();
            } catch (JMException e) {
                logHandler.error("Cannot unregister own MBeans: " + e, e);
            }
            for (JolokiaServiceLookup factory : serviceLookups) {
                factory.destroy();
            }
            for (Class<? extends JolokiaService> serviceType : getServiceTypes()) {
                Set<? extends JolokiaService> services = staticServices.get(serviceType);
                if (services != null) {
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
    }

    /** {@inheritDoc} */
    public <T extends JolokiaService> SortedSet<T> getServices(Class<T> pType) {
        SortedSet<T> services = (SortedSet<T>) staticServices.get(pType);
        SortedSet<T> ret = services != null ? new TreeSet<T>(services) : new TreeSet<T>();
        for (JolokiaServiceLookup factory : serviceLookups) {
            ret.addAll(factory.getServices(pType));
        }
        return ret;
    }

    /** {@inheritDoc} */
    public <T extends JolokiaService> T getService(Class<T> pType) {
        T ret = (T) staticLowServices.get(pType);
        int order = ret != null ? ret.getOrder() : Integer.MAX_VALUE;
        for (JolokiaServiceLookup factory : serviceLookups) {
            for (T service : (SortedSet<T>) factory.getServices(pType)) {
                if (service.getOrder() < order) {
                    ret = service;
                    order = ret.getOrder();
                }
            }
        }
        return ret;
    }


    // =======================================================================================================

    // Extract the order in which services should be initialized
    private List<Class<? extends JolokiaService>> getServiceTypes() {
        List<Class<? extends JolokiaService>> ret = new ArrayList<Class<? extends JolokiaService>>();
        for (Class type : SERVICE_TYPE_ORDER) {
            ret.add(type);
        }
        for (Class<? extends JolokiaService> staticType : staticServices.keySet()) {
            if (!ret.contains(staticType)) {
                ret.add(staticType);
            }
        }
        return ret;
    }

    /**
     * Register a MBean under a certain name to the platform MBeanServer.
     *
     * This method delegates to the {@link MBeanRegistry}.
     * 
     * @param pMBean MBean to register
     * @param pOptionalName optional name under which the bean should be registered. If not provided,
     * it depends on whether the MBean to register implements {@link javax.management.MBeanRegistration} or
     * not.
     *
     * @return the name under which the MBean is registered.
     */
    public final ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        return mbeanRegistry.registerMBean(pMBean, pOptionalName);
    }
}
