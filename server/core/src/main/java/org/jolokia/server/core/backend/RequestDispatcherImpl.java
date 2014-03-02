package org.jolokia.server.core.backend;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.RequestHandler;

/**
 * Manager object responsible for finding a {@link RequestHandler} and
 * dispatching the request.
 *
 * @author roland
 * @since 11.06.13
 */
class RequestDispatcherImpl implements RequestDispatcher {

    // Service manager for looking up services
    private final JolokiaContext jolokiaContext;

    /**
     * Create a dispatcher which is used to select the backend for processing the request
     *
     * @param pPJolokiaContext service manager for looking up all services
     */
    RequestDispatcherImpl(JolokiaContext pPJolokiaContext) {
        jolokiaContext = pPJolokiaContext;
    }

    /** {@inheritDoc} */
    public Object dispatch(JolokiaRequest pJolokiaRequest)
            throws JMException, IOException, NotChangedException {

        // Request handlers are looked up each time to cope with the dynamics e.g. in OSGi envs.
        boolean found = false;
        Object result = null;
        for (RequestHandler requestHandler : jolokiaContext.getServices(RequestHandler.class)) {
            if (requestHandler.canHandle(pJolokiaRequest)) {
                if (pJolokiaRequest.isExclusive()) {
                    return requestHandler.handleRequest(pJolokiaRequest,null);
                } else {
                    result = requestHandler.handleRequest(pJolokiaRequest,result);
                }
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("Internal error: No request handler found for handling " + pJolokiaRequest);
        }
        return result;
    }
}
