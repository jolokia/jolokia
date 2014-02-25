package org.jolokia.agent.core.backend;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.agent.core.request.JolokiaRequest;
import org.jolokia.agent.core.service.request.RequestHandler;

/**
 * Manager interface for dispatching a request to one {@link RequestHandler}.
 * This is the entry point for Jolokia in order to process a request.
 *
 * @author roland
 * @since 11.06.13
 */
public interface RequestDispatcher {

    /**
     * Dispatch a request to a single {@link RequestHandler}. This results a list with zero, one or more result
     * objects. If more than one result is returned, the results must be merged.
     *
     * @param pJolokiaRequest the request to dispatch
     * @return result of the dispatch operation.
     *
     * @throws NotChangedException the request handler detects no change for the requests' result and
     *                             hence returns without result.
     * @throws IOException IO Exception during the operation.
     * @throws JMException a JMX operation failed.
     */
    Object dispatch(JolokiaRequest pJolokiaRequest) throws JMException, NotChangedException, IOException;
}
