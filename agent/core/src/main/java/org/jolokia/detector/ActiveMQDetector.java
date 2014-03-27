package org.jolokia.detector;

import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * Detector for ActiveMQ
 *
 * @author roland
 * @since 27.03.14
 */
public class ActiveMQDetector extends AbstractServerDetector {

    /** {@inheritDoc} */
    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        String version = getSingleStringAttribute(pMBeanServerExecutor, "org.apache.activemq:type=Broker,*", "BrokerVersion");
        if (version == null) {
            return null;
        }
        return new ServerHandle("Apache","activemq",version, null);
    }
}
