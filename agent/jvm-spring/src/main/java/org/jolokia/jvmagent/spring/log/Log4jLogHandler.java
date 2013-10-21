package org.jolokia.jvmagent.spring.log;

import org.apache.log4j.Logger;
import org.jolokia.util.LogHandler;

/**
 * Loghandler using Log4j
 * @author roland
 * @since 21.10.13
 */
public class Log4jLogHandler implements LogHandler {

    private Logger logger;

    public Log4jLogHandler(String pCategory) {
        logger = Logger.getLogger(pCategory != null ? pCategory : "org.jolokia" );
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
