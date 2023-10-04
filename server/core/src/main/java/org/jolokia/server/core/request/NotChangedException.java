package org.jolokia.server.core.request;

/**
 * Exception thrown when an <code>ifModifiedSince</code> parameter was given and
 * the requested resourced doesnt has changed
 * @author roland
 * @since 07.03.13
 */
public class NotChangedException extends Exception {

    private final JolokiaRequest request;

    /**
     * Constructor
     * @param pRequest which lead to this exception
     */
    public NotChangedException(JolokiaRequest pRequest) {
        request = pRequest;
    }

    /**
     * Request which lead to this exception
     * @return request
     */
    public JolokiaRequest getRequest() {
        return request;
    }
}
