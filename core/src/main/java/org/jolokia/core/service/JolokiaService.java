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

/**
 * Interface describing a Jolokia Service. Jolokia Services are used within Jolokia
 * for various tasks. Each service has a specific type describing its API. Also, it has an
 * order which is used when multiple services exist. Services can be created in many ways, either
 * statically (and then registered at the {@link JolokiaServiceManager}) or dynamically via a
 * {@link JolokiaServiceLookup} (which is especially suited for looking up OSGi services).
 *
 * @author roland
 * @since 28.03.13
 */
public interface JolokiaService<T extends JolokiaService> extends Comparable<T> {

    // Marker interface for services which only want to take part in the
    // service's lifecycle and are never looked up.
    interface Init extends JolokiaService<Init> {}

    /**
     * Order of the service. The higher the number, the later in the list of services this service appears.
     * Default order is 100.
     *
     * @return the order of this service
     */
    int getOrder();

    /**
     * The service type which is used to distinguish the various services. The service type is an extension
     * of this base interface and add service specific methods to it
     *
     * @return service type
     */
    Class<T> getType();

    /**
     * Lifecycle method called when agent goes down.
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    void destroy() throws Exception;

    /**
     * Lifecycle method called when the services are initialized
     *
     * @param pJolokiaContext the jolokia context used
     */
    void init(JolokiaContext pJolokiaContext);
}
