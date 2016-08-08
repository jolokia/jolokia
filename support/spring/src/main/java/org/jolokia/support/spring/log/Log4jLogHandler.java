package org.jolokia.support.spring.log;

import org.apache.log4j.Logger;
import org.jolokia.server.core.service.api.LogHandler;

/**
 * Loghandler using Log4j
 * @author roland
 * @since 21.10.13
 */
public class Log4jLogHandler implements LogHandler {

    private Logger logger;

    /**
     * Constructor for a {@link LogHandler} using Log4j (version 1)
     *
     * @param pCategory the logging category. If null, org.jolokia is used as category
     */
    public Log4jLogHandler(String pCategory) {
        logger = Logger.getLogger(pCategory != null ? pCategory : "org.jolokia" );
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
