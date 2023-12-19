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

package org.jolokia.server.core.service.api;

import javax.management.*;

/**
 * Abstract base class for {@link JolokiaService}s.
 *
 * @author roland
 * @since 22.04.13
 */
public abstract class AbstractJolokiaService<T extends JolokiaService<?>> implements JolokiaService<T> {

    // service type of this Jolokia Service
    private final Class<T> type;

    // order number for this service
    private final int order;

    // context, valid after init
    private JolokiaContext jolokiaContext;

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
    public void destroy() throws Exception {
        jolokiaContext = null;
    }

    /**
     * Override if access to the JolokiaContext is
     * required.
     *
     * @param pJolokiaContext JolokiaContext used
     */
    public void init(JolokiaContext pJolokiaContext) {
        jolokiaContext = pJolokiaContext;
    }

    /** {@inheritDoc} */
    public int compareTo(T pOtherService) {
        int ret = getOrder() - pOtherService.getOrder();
        // 0 is returned if really equals. Otherwise, if the order is the same we use a random, albeit
        // deterministic order based on the hashcode
        return ret != 0 ? ret : equals(pOtherService) ? 0 : hashCode() - pOtherService.hashCode();
    }

    // =========================================================================================

    /**
     * Register an MBean with a unique qualifier for this agent.
     *
     * @param pName name of the MBean to register
     * @param pMBean the MBean to register
     */
    protected ObjectName registerJolokiaMBean(String pName, Object pMBean) {
        String objectNameS = getMBeanNameWithAgentId(pName);
        try {
            return jolokiaContext.registerMBean(pMBean,objectNameS);
        } catch (JMException e) {
            jolokiaContext.error("Cannot register MBean " + objectNameS + ": " + e,e);
            return null;
        }
    }

    /**
     * Unregister MBean with the given name. If it hasn't been registered before with {@link #registerJolokiaMBean(String, Object)}
     * then this is a no-op. Also, any error is logged but wont result in any exception
     *
     * @param oName name as returned during registration. If null, nothing happens
     */
    protected void unregisterJolokiaMBean(ObjectName oName) {
        if (jolokiaContext != null && oName != null) {
            try {
                jolokiaContext.unregisterMBean(oName);
            } catch (MBeanRegistrationException e) {
                jolokiaContext.error("Cannot unregister MBean " + oName + ": " + e,e);
            }
        }
    }

    protected JolokiaContext getJolokiaContext() {
        return jolokiaContext;
    }

    private String getMBeanNameWithAgentId(String pName) {
        AgentDetails details = jolokiaContext.getAgentDetails();
        String agentId = details.getAgentId();
        return pName + ",agent=" + agentId;
    }
}
