package org.jolokia.util;

/**
 * @author roland
 * @since 04.02.14
 */
public class StdoutLogHandler implements LogHandler {
    public void debug(String message) {
        System.out.println("D> " + message);
    }

    public void info(String message) {
        System.out.println("I> " + message);
    }

    public void error(String message, Throwable t) {
        System.out.println("E> " + message);
        t.printStackTrace();
    }
}
