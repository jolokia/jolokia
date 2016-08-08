package org.jolokia.support.spring.log;

import org.jolokia.server.core.service.api.LogHandler;
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

    /**
     * Constructor for a {@link LogHandler} using commons SL4J
     *
     * @param pCategory the logging category. If null, org.jolokia is used as category
     */
    public Sl4jLogHandler(String pCategory) {
        logger = LoggerFactory.getLogger(pCategory != null ? pCategory : "org.jolokia");
    }

    /** {@inheritDoc} */
    public void debug(String message) {
        logger.debug(message);
    }

    /** {@inheritDoc} */
    public void info(String message) {
        logger.info(message);
    }

    /** {@inheritDoc} */
    public void error(String message, Throwable t) {
        logger.error(message,t);
    }

    /** {@inheritDoc} */
    public boolean isDebug() {
        return logger.isDebugEnabled();
    }
}
