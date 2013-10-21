package org.jolokia.jvmagent.spring.log;

import org.jolokia.util.LogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loghandler using SL4J
 *
 * @author roland
 * @since 21.10.13
 */
public class Sl4jLogHandler implements LogHandler {

    private Logger logger;

    public Sl4jLogHandler(String pCategory) {
        logger = LoggerFactory.getLogger(pCategory != null ? pCategory : "org.jolokia");
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void error(String message, Throwable t) {
        logger.error(message,t);
    }

    public boolean isDebug() {
        return logger.isDebugEnabled();
    }
}
