package org.jolokia.core.service;

import org.jolokia.core.config.Configuration;
import org.jolokia.core.service.impl.JolokiaServiceManagerImpl;

/**
 * Factory class for creating a {@link JolokiaServiceManager}. It's a factory
 * in the classical sense providing a single static method.
 *
 * @author roland
 * @since 27.02.14
 */
public class JolokiaServiceManagerFactory {

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
        return new JolokiaServiceManagerImpl(pConfig,pLogHandler,pRestrictor);
    }
}
