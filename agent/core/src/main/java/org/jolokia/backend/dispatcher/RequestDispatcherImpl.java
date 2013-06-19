package org.jolokia.backend.dispatcher;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaServiceManager;

/**
 * Manager object responsible for finding a {@link RequestHandler} and
 * dispatching the request.
 *
 * @author roland
 * @since 11.06.13
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    // Service manager for looking up services
    private final JolokiaServiceManager serviceManager;

    /**
     * Create a dispatcher which is used to select the backend for processing the request
     *
     * @param pServiceManager service manager for looking up all services
     */
    public RequestDispatcherImpl(JolokiaServiceManager pServiceManager) {
        serviceManager = pServiceManager;

    }

    /** {@inheritDoc} */
    public DispatchResult dispatch(JmxRequest pJmxRequest) throws JMException, IOException, NotChangedException {

        // Request handlers are looked up each time to cope with the dynamics e.g. in OSGi envs.
        for (RequestHandler requestHandler : serviceManager.getServices(RequestHandler.class)) {
            if (requestHandler.canHandle(pJmxRequest)) {
                Object retValue = requestHandler.handleRequest(pJmxRequest);
                boolean useValueWithPath = requestHandler.useReturnValueWithPath(pJmxRequest);
                return new DispatchResult(retValue,useValueWithPath ? pJmxRequest.getPathParts() : null);
            }
        }
        return null;
    }

}
