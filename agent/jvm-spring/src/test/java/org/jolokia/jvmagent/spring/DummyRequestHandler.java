package org.jolokia.jvmagent.spring;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.AbstractJolokiaService;

/**
 * @author roland
 * @since 22.10.13
 */
public class DummyRequestHandler extends AbstractJolokiaService<RequestHandler>
        implements RequestHandler {

    protected DummyRequestHandler() {
        super(RequestHandler.class,0);
    }

    public Object handleRequest(JmxRequest pJmxReq) throws JMException, IOException, NotChangedException {
        return null;
    }

    public boolean canHandle(JmxRequest pJmxRequest) {
        return false;
    }

    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        return false;
    }
}
