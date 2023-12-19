package org.jolokia.server.core.request;

/**
 * Exception thrown when no response should be returned.
 *
 * @author roland
 * @since 07.03.13
 */
public class EmptyResponseException extends Exception {

    /**
     * Constructor
     */
    public EmptyResponseException() {
    }
}
