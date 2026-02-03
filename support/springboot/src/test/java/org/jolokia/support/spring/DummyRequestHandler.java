package org.jolokia.support.spring;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.request.AbstractRequestHandler;

/**
 * @author roland
 * @since 22.10.13
 */
public class DummyRequestHandler extends AbstractRequestHandler {

    protected DummyRequestHandler() {
        super("dummy",0);
    }

    public Object handleRequest(JolokiaRequest pJmxReq, Object pPreviousResult) {
        return null;
    }

    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return false;
    }

}
