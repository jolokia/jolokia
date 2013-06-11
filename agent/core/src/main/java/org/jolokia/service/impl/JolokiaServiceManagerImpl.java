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

import javax.management.JMException;

import org.jolokia.config.Configuration;
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
            jolokiaContext = new JolokiaContextImpl(configuration, logHandler,restrictor);
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
                 jolokiaContext.destroy();
             } catch (JMException e) {
                 getLogHandler().error("Cannot destroy the Jolokia context: " + e,e);
             }
             for (JolokiaServiceFactory factory : serviceFactories) {
                 factory.destroy();
             }
         }
         isInitialized = false;
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
