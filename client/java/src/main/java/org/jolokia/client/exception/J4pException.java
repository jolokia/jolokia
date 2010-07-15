package org.jolokia.client.exception;

/**
 * Base exception potentially raised when communicating with the server
 * @author roland
 * @since Jun 8, 2010
 */
public class J4pException extends Exception {

    /**
     * Constructor with a simple message
     *
     * @param message exception description
     */
    public J4pException(String message) {
        super(message);
    }

    /**
     * Exception with a nested exception
     *
     * @param message description of this exception
     * @param cause exception causing this exception
     */
    public J4pException(String message, Throwable cause) {
        super(message, cause);
    }

}
