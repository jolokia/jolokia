package org.jolokia.server.core.detector;

import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;

import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * A fallback detector which always returns a null handle
 *
 * @author roland
 * @since 03.03.14
 */
class FallbackServerDetector implements ServerDetector {

    /** {@inheritDoc} */
    public String getName() {
        return "fallback";
    }

    /** {@inheritDoc} */
    public void init(Map<String, Object> pConfig) { }

    /**
     * {@inheritDoc}
     */
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        return ServerHandle.NULL_SERVER_HANDLE;
    }

    /** {@inheritDoc} */
    public Set<MBeanServerConnection> getMBeanServers() {
        return null;
    }

    /** {@inheritDoc} */
    public RequestInterceptor getRequestInterceptor(MBeanServerAccess pMBeanServerAccess) {
        return null;
    }

    /** {@inheritDoc} */
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    /** {@inheritDoc} */
    public int compareTo(ServerDetector pDetector) {
        return getOrder() - pDetector.getOrder();
    }

    @Override
    /** {@inheritDoc} */
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    /** {@inheritDoc} */
    public int hashCode() {
        return 42;
    }

}
