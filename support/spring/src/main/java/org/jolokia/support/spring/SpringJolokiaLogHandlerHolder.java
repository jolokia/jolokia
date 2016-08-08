package org.jolokia.support.spring;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.jolokia.server.core.service.api.LogHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

/**
 * A holder for logging configuration for the spring agent
 *
 * @author roland
 * @since 17.10.13
 */
public class SpringJolokiaLogHandlerHolder implements InitializingBean {

    // the final log handler to use
    private LogHandler logHandler;

    // log handler type
    private String type;

    // logging category
    private String category;

    /** {@inheritDoc} */
    public void afterPropertiesSet() {
        if (logHandler == null) {
            if (StringUtils.hasLength(type)) {
                LogHandlerType lht = LogHandlerType.byType(type);
                if (lht == null) {
                    throw new IllegalArgumentException("No loghandler with type " + type + " known");
                }
                logHandler = lht.createLogHandler(StringUtils.hasLength(category) ? category : null);
            } else {
                throw new IllegalArgumentException("Neither 'log-ref' nor 'type' given. Please provide one of them");
            }
        }
    }

    public void setLogHandler(LogHandler pLogHandler) {
        logHandler = pLogHandler;
    }

    public void setType(String pType) {
        type = pType;
    }

    public void setCategory(String pCategory) {
        category = pCategory;
    }

    public LogHandler getLogHandler() {
        return logHandler;
    }

    // ==============================================================================

    // Enumeration for the various log handler which are looked up by reflection in order
    // to avoid hard dependencies on all those logging frameworks
    enum LogHandlerType {
        STDOUT("stdout","org.jolokia.server.core.service.impl.StdoutLogHandler"),
        QUIET("stdout","org.jolokia.server.core.service.impl.QuietLogHandler"),
        JUL("jul","org.jolokia.server.core.service.impl.JulLogHandler"),
        LOG4J("log4j","org.jolokia.support.spring.log.Log4jLogHandler"),
        LOG4J2("log4j2","org.jolokia.support.spring.log.Log4j2LogHandler"),
        SL4J("sl4j","org.jolokia.support.spring.log.Sl4jLogHandler"),
        COMMONS("commons","org.jolokia.support.spring.log.CommonsLogHandler");


        private final String className;
        private final String type; // NOPMD

        LogHandlerType(String pType, String pClassName) {
            this.className = pClassName;
            this.type = pType;
        }

        static LogHandlerType byType(String name) {
            for (LogHandlerType t : values()) {
                if (t.type.equals(name)) {
                    return t;
                }
            }
            return null;
        }

        LogHandler createLogHandler(String category) {
            try {
                Class<LogHandler> clazz = (Class<LogHandler>) this.getClass().getClassLoader().loadClass(className);
                Constructor<LogHandler> ctr = clazz.getConstructor(String.class);
                return ctr.newInstance(category);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            } catch (InstantiationException e) {
                throw new IllegalStateException(e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

    }
}
