package org.jolokia.backend.dispatcher;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JolokiaRequest;
import org.jolokia.service.JolokiaContext;

/**
 * Manager object responsible for finding a {@link RequestHandler} and
 * dispatching the request.
 *
 * @author roland
 * @since 11.06.13
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    // Service manager for looking up services
    private final JolokiaContext jolokiaContext;

    /**
     * Create a dispatcher which is used to select the backend for processing the request
     *
     * @param pPJolokiaContext service manager for looking up all services
     */
    public RequestDispatcherImpl(JolokiaContext pPJolokiaContext) {
        jolokiaContext = pPJolokiaContext;
    }

    /** {@inheritDoc} */
    public DispatchResult dispatch(JolokiaRequest pJolokiaRequest) throws JMException, IOException, NotChangedException {

        // Request handlers are looked up each time to cope with the dynamics e.g. in OSGi envs.
        for (RequestHandler requestHandler : jolokiaContext.getServices(RequestHandler.class)) {
            if (requestHandler.canHandle(pJolokiaRequest)) {
                Object retValue = requestHandler.handleRequest(pJolokiaRequest);
                boolean useValueWithPath = requestHandler.useReturnValueWithPath(pJolokiaRequest);
                return new DispatchResult(retValue,useValueWithPath ? pJolokiaRequest.getPathParts() : null);
            }
        }
        return null;
    }

}
