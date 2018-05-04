package org.jolokia.http;

import java.io.IOException;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;

import org.jolokia.backend.RequestDispatcher;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.handler.RequestHandlerManager;
import org.jolokia.request.JmxRequest;
import org.jolokia.restrictor.Restrictor;

/**
 * Temporary dispatcher added to avoid confusion for users which use 1.5.0 as a drop in replacement
 * for a JSR-160 proxy server. Otherwise Jolokia requests for a JSR-160 target will be silently
 * ignored and the MBeans of the Proxy server would be queried.
 *
 * @author roland
 * @since 08.02.18
 */
public class Jsr160ProxyNotEnabledByDefaultAnymoreDispatcher implements RequestDispatcher {


    // Constructor required for the backend to be able to crete the constructor
    public Jsr160ProxyNotEnabledByDefaultAnymoreDispatcher(Converters pConverters,
                                                           ServerHandle pServerInfo,
                                                           Restrictor pRestrictor,
                                                           Configuration pConfig) {
    }

    @Override
    public Object dispatchRequest(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        throw new IllegalArgumentException(
            "No JSR-160 proxy is enabled by default since Jolokia 1.5.0. " +
            "Please refer to the reference manual, section \"Proxy Mode\", " +
            "for how to switch on JSR-160 proxy mode again.");
    }

    @Override
    public boolean canHandle(JmxRequest pJmxRequest) {
        return pJmxRequest.getTargetConfig() != null;
    }

    @Override
    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        return false;
    }
}
