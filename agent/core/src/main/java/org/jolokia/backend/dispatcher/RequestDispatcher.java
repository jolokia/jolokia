package org.jolokia.backend.dispatcher;

import java.io.IOException;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;

/**
 * @author roland
 * @since 11.06.13
 */
public interface RequestDispatcher {
    DispatchResult dispatch(JmxRequest pJmxRequest) throws AttributeNotFoundException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, MBeanException;
}
