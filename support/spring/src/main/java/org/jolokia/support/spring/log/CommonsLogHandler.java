package org.jolokia.support.spring.log;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jolokia.server.core.service.api.LogHandler;

/**
 * Log Handler useing SL4J (which is a dependency of Spring anyways.
 *
 * @author roland
 * @since 17.10.13
 */
public class CommonsLogHandler implements LogHandler {

    private Log log;

    /**
     * Constructor for a {@link LogHandler} using commons logging
     *
     * @param pCategory the logging category. If null, org.jolokia is used as category
     */
    public CommonsLogHandler(String pCategory) {
        log = LogFactory.getLog(pCategory != null ? pCategory : "org.jolokia");
    }

    /** {@inheritDoc} */
    public void debug(String message) {
        log.debug(message);
    }

    /** {@inheritDoc} */
    public void info(String message) {
        log.info(message);
    }

    /** {@inheritDoc} */
    public void error(String message, Throwable t) {
        log.error(message,t);
    }

    /** {@inheritDoc} */
    public boolean isDebug() {
        return log.isDebugEnabled();
    }

}
