package org.jolokia.backend.dispatcher;

import java.io.IOException;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JolokiaRequest;

/**
 * Manager interface for dispatching a request to one {@link RequestHandler}.
 * This is the entry point for Jolokia in order to process a request.
 *
 * @author roland
 * @since 11.06.13
 */
public interface RequestDispatcher {

    /**
     * Dispatch a request to a single {@link RequestHandler}.
     *
     * @param pJolokiaRequest the request to dispatch
     * @return result of the dispatch operation.
     *
     * @throws NotChangedException the request handler detects no change for the requests' result and
     *                             hence returns without result.
     * @throws IOException IO Exception during the operation.
     * @throws JMException a JMX operation failed.
     */
    DispatchResult dispatch(JolokiaRequest pJolokiaRequest) throws JMException, NotChangedException, IOException;
}
