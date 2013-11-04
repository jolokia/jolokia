package org.jolokia.jvmagent.spring.log;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jolokia.util.LogHandler;

/**
 * Loghandler based on <code>java.util.logging</code>. <code>FINER</code>
 * is used for level <code>DEBUG</code>
 *
 * @author roland
 * @since 17.10.13
 */
public class JulLogHandler implements LogHandler {

    private Logger logger;

    /**
     * Constructor for a {@link LogHandler} using java util logging
     *
     * @param pCategory the logging category. If null, org.jolokia is used as category
     */
    public JulLogHandler(String pCategory) {
        logger = Logger.getLogger(pCategory != null ? pCategory : "org.jolokia");
    }

    /** {@inheritDoc} */
    public void debug(String message) {
        logger.finer(message);
    }

    /** {@inheritDoc} */
    public void info(String message) {
        logger.info(message);
    }

    /** {@inheritDoc} */
    public void error(String message, Throwable t) {
        logger.log(Level.SEVERE,message,t);
    }

    /** {@inheritDoc} */
    public boolean isDebug() {
        return logger.isLoggable(Level.FINER);
    }
}
