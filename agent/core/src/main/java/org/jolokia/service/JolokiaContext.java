package org.jolokia.service;

import java.util.List;
import java.util.Map;

import org.jolokia.backend.MBeanServerHandler;
import org.jolokia.backend.RequestDispatcher;
import org.jolokia.config.*;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

/**
 * @author roland
 * @since 09.04.13
 */
public interface JolokiaContext extends LogHandler, Restrictor {

    <T extends JolokiaService> List<T> getServices(Class<T> pServiceType);

    <T extends JolokiaService> T getSingleService(Class<T> pServiceType);

    Configuration getConfiguration();

    String getConfig(ConfigKey pOption);

    int getConfigAsInt(ConfigKey pOption);

    boolean getConfigAsBoolean(ConfigKey pOption);

    List<RequestDispatcher> getRequestDispatchers();

    MBeanServerHandler getMBeanServerHandler();

    Converters getConverters();

    ServerHandle getServerHandle();

    ProcessingParameters getProcessingParameters(Map<String, String> pRet);
}