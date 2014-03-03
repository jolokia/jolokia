package org.jolokia.server.core.service.api;

import javax.management.ObjectName;

/**
 * @author roland
 * @since 02.03.14
 */
abstract public class MBeanRegisteringService extends  AbstractJolokiaService<JolokiaService.Init> implements JolokiaService.Init {

    private JolokiaContext jolokiaContext;

    private String agentId;

    private ObjectName objectName;

    /** {@inheritDoc} */
    protected MBeanRegisteringService(int pOrderId) {
        super(JolokiaService.Init.class, pOrderId);
    }


    protected abstract String getObjectName();

}
