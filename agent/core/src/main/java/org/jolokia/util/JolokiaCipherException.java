package org.jolokia.util;

/**
 * @author nevenr
 * @since  12/09/2015
 */
public class JolokiaCipherException extends Exception {
    public JolokiaCipherException() {
    }

    public JolokiaCipherException(String message) {
        super(message);
    }

    public JolokiaCipherException(Throwable cause) {
        super(cause);
    }

    public JolokiaCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
