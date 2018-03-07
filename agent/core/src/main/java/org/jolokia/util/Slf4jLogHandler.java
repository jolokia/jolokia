package org.jolokia.util;

public class Slf4jLogHandler implements org.jolokia.util.LogHandler {

    private final org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger("org.jolokia");

    @Override
    public void debug(String message) {
        slf4jLogger.debug(message);
    }

    @Override
    public void info(String message) {
        slf4jLogger.info(message);
    }

    @Override
    public void error(String message, Throwable t) {
        slf4jLogger.error(message, t);
    }
}
