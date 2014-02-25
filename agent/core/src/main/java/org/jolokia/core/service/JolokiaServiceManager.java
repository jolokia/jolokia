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

package org.jolokia.core.service;

import java.util.SortedSet;

import org.jolokia.core.config.Configuration;
import org.jolokia.core.restrictor.Restrictor;
import org.jolokia.core.util.LogHandler;

/**
 * The service manager is responsible for building/looking up {@link JolokiaService}s and for creating a
 * {@link JolokiaContext}.
 *
 * Service can be added or created in various ways:
 *
 * <ul>
 *    <li>{@link #addService(JolokiaService)} adds <em>static</em> services which already exist.</li>
 *    <li>{@link #addServices(JolokiaServiceCreator)} can be used to add a bunch of static services via an lookup</li>
 *    <li>{@link #addServiceLookup(JolokiaServiceLookup)} add as a lookup handler which will check for services each
 *        time {@link #getServices(Class)} is called</li>
 * </ul>
 *
 * Every registered service or lookup participate in the lifecycle of this JolokiaServiceManager. I.e. their
 * <code>init()</code> method is called during {@link #start()} and their <code>destroy()</code> method during {@link #stop()}.
 *
 * @author roland
 * @since 22.04.13
 */
public interface JolokiaServiceManager  {

    /**
     * Add a single service. This service is static in so far as it is in use
     * until the end of this service manager
     *
     * @param pService service to add
     */
    void addService(JolokiaService pService);

    /**
     * Add a service factory for dynamically looking up services. This is
     * especially useful when services can come and go dynamically.
     * {@link JolokiaServiceLookup#getServices(Class)} will
     * be always called when looking up services within the core.
     *
      * @param pLookup lookup for services to add.
     */
    void addServiceLookup(JolokiaServiceLookup pLookup);

    /**
     * A {@link JolokiaServiceCreator} is responsible for creating one or more service. These will
     * be created right during this call and have the same lifecycle as static services
     * added with {@link #addService(JolokiaService)}
     *
     * @param pServiceCreator creator for creating the service
     */
    void addServices(JolokiaServiceCreator pServiceCreator);

    /**
     * Start up the service manager. All static services are initialized via its lifecycle method
     * {@link JolokiaService#init(JolokiaContext)}.
     * For dynamic services, the lookup service obtains a handle to the
     * created {@link JolokiaContext} via {@link JolokiaServiceLookup#init(JolokiaContext)}.
     * This method is ominpotent as it can be called multiple in sequence returning always the same
     * {@link JolokiaContext}.
     *
     * @return the created jolokia context which can be used directly
     */
    JolokiaContext start();

    /**
     * Stop the service manager and all services by calling their lifecycle methods
     * {@link JolokiaService#destroy()} and {@link JolokiaServiceLookup#destroy()}
     * on all static and dynamic services.
     */
    void stop();

    /**
     * Get all services of a certain type currently registered. Static services
     * are returned directly, for dynamic services a lookup to the service factory is
     * performed.
     *
     * @param pType service type to fetch
     * @return list of services detected or an empty list
     */
    <T extends JolokiaService> SortedSet<T> getServices(Class<T> pType);

    /**
     * Get a single service. If more than one service of the given type has been
     * registered, return the one with the highest order. If no one has been registered
     * return <code>null</code>
     *
     * @param pType requested service type
     * @return the requested service or null if none has been registered
     */
    <T extends JolokiaService> T getService(Class<T> pType);

    /**
     * Get the overall configuration
     *
     * @return configuration
     */
    Configuration getConfiguration();

    /**
     * Get the loghandler used for logging
     *
     * @return loghandler
     */
    LogHandler getLogHandler();

    /**
     * Get the restrictor
     *
     * @return restrictor
     */
    Restrictor getRestrictor();
}
