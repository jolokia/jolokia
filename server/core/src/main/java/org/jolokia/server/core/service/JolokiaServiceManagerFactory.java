package org.jolokia.server.core.service;

import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.impl.JolokiaServiceManagerImpl;

/**
 * Factory class for creating a {@link JolokiaServiceManager}. It's a factory
 * in the classical sense providing a single static method. There will be very, very
 * likely never any other implementation of a {@link JolokiaServiceManager}.
 *
 * @author roland
 * @since 27.02.14
 */
public final class JolokiaServiceManagerFactory {

    private JolokiaServiceManagerFactory() {}

    /**
     * Create the implementation of a service manager
     *
     * @param pConfig configuration to use
     * @param pLogHandler the logger
     * @param pRestrictor restrictor to apply
     */
    public static JolokiaServiceManager createJolokiaServiceManager(Configuration pConfig,
                                                                    LogHandler pLogHandler,
                                                                    Restrictor pRestrictor) {
        return createJolokiaServiceManager(pConfig, pLogHandler, pRestrictor, null);
    }

    /**
     * Create the implementation of a service manager with an additional hook for
     * adding server detectors
     *
     * @param pConfig configuration to use
     * @param pLogHandler the logger
     * @param pRestrictor restrictor to apply
     * @param pDetectorLookup lookup class used for finding detectors when the service manager starts up
     */
    public static JolokiaServiceManager createJolokiaServiceManager(Configuration pConfig,
                                                                    LogHandler pLogHandler,
                                                                    Restrictor pRestrictor,
                                                                    ServerDetectorLookup pDetectorLookup) {
        return new JolokiaServiceManagerImpl(pConfig,pLogHandler,pRestrictor,pDetectorLookup);
    }
}
