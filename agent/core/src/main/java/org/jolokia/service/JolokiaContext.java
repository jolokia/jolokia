package org.jolokia.service;

import java.util.List;

import org.jolokia.backend.MBeanServerHandler;
import org.jolokia.backend.RequestDispatcher;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

/**
 * @author roland
 * @since 09.04.13
 */
public interface JolokiaContext extends LogHandler, Restrictor, Configuration {

    //<T extends JolokiaService> List<T> getServices(Class<T> pServiceType);

    //<T extends JolokiaService> T getSingleService(Class<T> pServiceType);

    // ============================
    // As config interface

    // =============================

    List<RequestDispatcher> getRequestDispatchers();

    MBeanServerHandler getMBeanServerHandler();

    Converters getConverters();

    ServerHandle getServerHandle();

    // TODO: Shouldnt this part of the LogHandler API ?
    boolean isDebug();
}