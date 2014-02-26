package org.jolokia.jvmagent.spring;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.core.request.NotChangedException;
import org.jolokia.core.service.request.AbstractRequestHandler;
import org.jolokia.core.request.JolokiaRequest;

/**
 * @author roland
 * @since 22.10.13
 */
public class DummyRequestHandler extends AbstractRequestHandler {

    protected DummyRequestHandler() {
        super("dummy",0);
    }

    public Object handleRequest(JolokiaRequest pJmxReq, Object pPreviousResult) throws JMException, IOException, NotChangedException {
        return null;
    }

    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return false;
    }

}
