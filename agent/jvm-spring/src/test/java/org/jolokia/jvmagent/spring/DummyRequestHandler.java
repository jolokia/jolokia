package org.jolokia.jvmagent.spring;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JolokiaRequest;
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

    public Object handleRequest(JolokiaRequest pJmxReq) throws JMException, IOException, NotChangedException {
        return null;
    }

    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return false;
    }

    public boolean useReturnValueWithPath(JolokiaRequest pJolokiaRequest) {
        return false;
    }
}
