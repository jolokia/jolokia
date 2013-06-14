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
    private Class<? extends JolokiaService> type;

    // order number for this service
    protected int order;


    /**
     * Create this base for the given service type
     *
     * @param pType type of this service
     */
    protected JolokiaServiceBase(Class<? extends JolokiaService> pType) {
        this(pType,DEFAULT_ORDER);
    }

    /**
     * Consruction of a base service for a given type and order
     *
     * @param pType service type
     * @param pOrder order
     */
    protected JolokiaServiceBase(Class<? extends JolokiaService> pType, int pOrder) {
        type = pType;
        order = pOrder;
    }

    /** {@inheritDoc} */
    public int getOrder() {
        return order;
    }

    /** {@inheritDoc} */
    public Class<? extends JolokiaService> getType() {
        return type;
    }

    /**
     * Override for hooking into the lifecycle
     */
    public void destroy() throws Exception {
    }

    /**
     * Override if access to the JolokiaContext is
     * required.
     *
     * @param pJolokiaContext JolokiaContext used
     */
    public void init(JolokiaContext pJolokiaContext) {
    }

    /** {@inheritDoc} */
    public int compareTo(Object o) {
        JolokiaService service1 = (JolokiaService) o;
        return service1.getOrder() - getOrder();
    }
}
