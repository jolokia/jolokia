package org.jolokia.util;

import java.io.IOException;

import javax.management.*;

import org.jolokia.backend.LocalRequestHandler;
import org.jolokia.backend.dispatcher.DispatchResult;
import org.jolokia.backend.dispatcher.RequestDispatcher;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaContext;

/**
 * @author roland
 * @since 11.06.13
 */
public class TestRequestDispatcher implements RequestDispatcher {

    LocalRequestHandler handler;

    public TestRequestDispatcher() {
        this(new TestJolokiaContext());
    }

    public TestRequestDispatcher(JolokiaContext pCtx) {
        handler = new LocalRequestHandler(0);
        handler.init(pCtx);
    }

    public DispatchResult dispatch(JmxRequest pJmxRequest) throws AttributeNotFoundException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, MBeanException {
        return new DispatchResult(handler.handleRequest(pJmxRequest),
                                  handler.useReturnValueWithPath(pJmxRequest) ? pJmxRequest.getPathParts() : null);
    }

    public void destroy() {
    }
}
