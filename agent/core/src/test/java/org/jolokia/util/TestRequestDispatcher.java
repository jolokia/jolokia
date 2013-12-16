package org.jolokia.util;

import java.io.IOException;

import javax.management.*;

import org.jolokia.backend.LocalRequestHandler;
import org.jolokia.backend.dispatcher.RequestDispatcher;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JolokiaRequest;
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

    public Object dispatch(JolokiaRequest pJolokiaRequest) throws AttributeNotFoundException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, MBeanException {
        return handler.handleRequest(pJolokiaRequest,null);
    }

    public void destroy() {
    }
}
