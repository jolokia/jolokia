package org.jolokia.jvmagent.spring.log;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jolokia.util.LogHandler;

/**
 * Log Handler useing SL4J (which is a dependency of Spring anyways.
 *
 * @author roland
 * @since 17.10.13
 */
public class CommonsLogHandler implements LogHandler {

    private Log log;

    public CommonsLogHandler(String pCategory) {
        log = LogFactory.getLog(pCategory != null ? pCategory : "org.jolokia");
    }

    public void debug(String message) {
        log.debug(message);
    }

    public void info(String message) {
        log.info(message);
    }

    public void error(String message, Throwable t) {
        log.error(message,t);
    }

    public boolean isDebug() {
        return log.isDebugEnabled();
    }

}
