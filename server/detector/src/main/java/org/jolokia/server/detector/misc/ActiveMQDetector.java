package org.jolokia.server.detector.misc;


import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.detector.jee.AbstractServerDetector;

/**
 * Detector for ActiveMQ
 *
 * @author roland
 * @since 27.03.14
 */
public class ActiveMQDetector extends AbstractServerDetector {

    /**
     * Create detector with the given order
     *
     * @param pOrder detector's order
     */
    public ActiveMQDetector(int pOrder) {
        super("activemq", pOrder);
    }

    /** {@inheritDoc} */
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        String version = getSingleStringAttribute(pMBeanServerAccess, "org.apache.activemq:type=Broker,*", "BrokerVersion");
        if (version == null) {
            return null;
        }
        return new DefaultServerHandle("Apache",getName(),version);
    }
}
