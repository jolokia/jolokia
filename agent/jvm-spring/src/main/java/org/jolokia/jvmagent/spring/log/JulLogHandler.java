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

    public JulLogHandler(String category) {
        logger = Logger.getLogger(category != null ? category : "org.jolokia");
    }

    public void debug(String message) {
        logger.finer(message);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void error(String message, Throwable t) {
        logger.log(Level.SEVERE,message,t);
    }

    public boolean isDebug() {
        return logger.isLoggable(Level.FINER);
    }
}
