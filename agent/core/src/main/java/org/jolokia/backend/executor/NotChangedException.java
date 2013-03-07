package org.jolokia.backend.executor;

import org.jolokia.request.JmxRequest;

/**
 * Exception thrown when an <code>ifModifiedSince</code> parameter was given and
 * the requested resourced doesnt has changed
 * @author roland
 * @since 07.03.13
 */
public class NotChangedException extends Exception {

    private JmxRequest request;

    /**
     * Constructor
     * @param pRequest which lead to this exception
     */
    public NotChangedException(JmxRequest pRequest) {
        request = pRequest;
    }

    /**
     * Request which lead to this exception
     * @return request
     */
    public JmxRequest getRequest() {
        return request;
    }
}
