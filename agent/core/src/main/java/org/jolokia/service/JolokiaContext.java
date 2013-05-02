package org.jolokia.service;

import java.util.List;
import java.util.Set;

import org.jolokia.backend.MBeanServerHandler;
import org.jolokia.backend.RequestDispatcher;
import org.jolokia.config.ConfigKey;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

/**
 * @author roland
 * @since 09.04.13
 */
public interface JolokiaContext extends LogHandler, Restrictor {

    //<T extends JolokiaService> List<T> getServices(Class<T> pServiceType);

    //<T extends JolokiaService> T getSingleService(Class<T> pServiceType);

    // ============================
    // As config interface

    // =============================

    List<RequestDispatcher> getRequestDispatchers();

    MBeanServerHandler getMBeanServerHandler();

    Converters getConverters();

    ServerHandle getServerHandle();

    /**
     * Get a configuration value if set as configuration or the default
     * value if not
     *
     * @param pKey the configuration key to lookup
     * @return the configuration value or the default value if no configuration
     *         was given.
     */
    String getConfig(ConfigKey pKey);

    /**
     * Get all keys stored in this configuration
     */
    Set<ConfigKey> getConfigKeys();
}