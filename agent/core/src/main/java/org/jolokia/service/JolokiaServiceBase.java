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

package org.jolokia.service;

/**
 * Abstract base class for {@link JolokiaService}s.
 *
 * @author roland
 * @since 22.04.13
 */
public abstract class JolokiaServiceBase implements JolokiaService {

    // default order value for a jolokia service
    private static final int DEFAULT_ORDER = 1000;

    // service type of this Jolokia Service
    private ServiceType type;

    // order number for this service
    protected int order;

    /**
     * Create this base for the given service type
     *
     * @param pType type of this service
     */
    protected JolokiaServiceBase(ServiceType pType) {
        this(pType,DEFAULT_ORDER);
    }

    /**
     * Consructio of a base service for a given type and order
     *
     * @param pType service type
     * @param pOrder order
     */
    protected JolokiaServiceBase(ServiceType pType, int pOrder) {
        type = pType;
        order = pOrder;
    }

    /** {@inheritDoc} */
    public int getOrder() {
        return order;
    }

    /** {@inheritDoc} */
    public ServiceType getType() {
        return type;
    }

    /**
     * Override for hooking into the lifecycle
     */
    public void destroy() {
    }

    /**
     * Override if access to the JolokiaContext is
     * required.
     *
     * @param pServiceManager service manager for accessing other services
     */
    public void init(JolokiaServiceManager pServiceManager) {
    }
}
