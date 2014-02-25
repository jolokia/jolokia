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

package org.jolokia.agent.core.service;

/**
 * Abstract base class for {@link JolokiaService}s.
 *
 * @author roland
 * @since 22.04.13
 */
public abstract class AbstractJolokiaService<T extends JolokiaService> implements JolokiaService<T> {

    // service type of this Jolokia Service
    private Class<T> type;

    // order number for this service
    private int order;

    /**
     * Construction of a base service for a given type and order
     *
     * @param pType service type
     * @param pOrderId order id. A user of JolokiaService <em>must ensure</em> that the given
     *                 order id is unique for the given type. It used for ordering the services but is also
     *                 used as an id when storing it in a set.
     */
    protected AbstractJolokiaService(Class<T> pType, int pOrderId) {
        type = pType;
        order = pOrderId;
    }

    /** {@inheritDoc} */
    public int getOrder() {
        return order;
    }

    /** {@inheritDoc} */
    public Class<T> getType() {
        return type;
    }

    /**
     * Override for hooking into the lifecycle
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void destroy() throws Exception { }

    /**
     * Override if access to the JolokiaContext is
     * required.
     *
     * @param pJolokiaContext JolokiaContext used
     */
    public void init(JolokiaContext pJolokiaContext) { }

    /** {@inheritDoc} */
    public int compareTo(T o) {
        return getOrder() - o.getOrder();
    }
}
