package org.jolokia.server.core.service.impl;

import java.util.*;

import javax.management.MBeanServerConnection;

import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.util.LocalServiceFactory;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.detector.*;

/**
 * Classpath scanner for detectors
 *
 * @author roland
 * @since 28.02.14
 */
public class ClasspathServerDetectorLookup implements ServerDetectorLookup {

    // lookup called before this lookup is called
    private final ServerDetectorLookup chainedLookup;

    /**
     * New detector which lookup from the classpath
     *
     * @param pChainedLookup a potentially lookup which is called and whose detectors are prepended to the one found
     *                       via classpath lookup
     */
    public ClasspathServerDetectorLookup(ServerDetectorLookup pChainedLookup) {
        chainedLookup = pChainedLookup;
    }

    /** {@inheritDoc} */
    public List<ServerDetector> lookup() {
        List<ServerDetector> detectors =
                chainedLookup != null ?
                        new ArrayList<ServerDetector>(chainedLookup.lookup()) :
                        new ArrayList<ServerDetector>();
        detectors.addAll(
                LocalServiceFactory.<ServerDetector>createServices("META-INF/jolokia/detectors-default",
                                                                   "META-INF/jolokia/detectors")
                        );
        detectors.add(new FallbackServerDetector());
        return detectors;
    }

    // ==================================================================================
    // Fallback server detector which matches always and comes last
    private static class FallbackServerDetector implements ServerDetector {

        public String getName() {
            return "fallback";
        }

        public void init(Map<String, Object> pConfig) {

        }

        /** {@inheritDoc} */
        public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
            return ServerHandle.NULL_SERVER_HANDLE;
        }

        public Set<MBeanServerConnection> getMBeanServers() {
            return null;
        }

        public RequestInterceptor getRequestInterceptor(MBeanServerAccess pMBeanServerAccess) {
            return null;
        }

        public int getOrder() {
            return 10000;
        }

        public int compareTo(ServerDetector pDetector) {
            return getOrder() - pDetector.getOrder();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }
}
