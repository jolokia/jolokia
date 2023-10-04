package org.jolokia.server.core.osgi;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import jakarta.servlet.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.http.AgentServlet;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.impl.OsgiJolokiaServiceFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Extended here for the sole purpose of exporting
 * this servlet to the outside in order that
 * it can be instantiated from another bundle.
 *
 * This service also tracks the availability of a log
 * service in order redirect the servlet logging to
 * the log service (if available). Otherwise it uses
 * the servlet's logging facility as fallback.
 *
 * @author roland
 * @since 10.02.11
 */
public class OsgiAgentServlet extends AgentServlet {

    private static final long serialVersionUID = 23L;

    // Context given in the constructor
    private BundleContext bundleContextGiven;

    // Tracker to be used for the LogService
    private ServiceTracker<LogService, LogService> logTracker;

    // A lookup which checks for OSGi detector services
    private ServerDetectorLookup osgiDetectorLookup;

    /**
     * Constructor with an empty context
     */
    public OsgiAgentServlet() {
        this(null);
    }

    /**
     * Constructor which associates this servlet with a bundle context
     *
     * @param pContext bundle context to associate with
     */
    public OsgiAgentServlet(BundleContext pContext) {
        this (pContext,null);
    }

    /**
     * Constructor with a bundle context and a given restrictor
     *
     * @param pContext associated bundle context
     * @param pRestrictor restrictor to use or <code>null</code> if the default
     *        lookup mechanism should be used
     */
    public OsgiAgentServlet(BundleContext pContext, Restrictor pRestrictor) {
        super(pRestrictor);
        bundleContextGiven = pContext;
    }

    /** {@inheritDoc} */
    @Override
    public void init(ServletConfig pServletConfig) throws ServletException {
        // We are making the bundle context available here as a thread local
        // so that the server detector has access to the bundle in order to detect
        // the Osgi-Environment
        BundleContext ctx = getBundleContext(pServletConfig);
        osgiDetectorLookup = new DelegatingServerDetectorLookup(ctx);
        super.init(pServletConfig);
    }

    /**
     * Get an OSGi based lookup mechanism for finding all detectors available during
     * @return lookup instance
     */
    @Override
    protected ServerDetectorLookup getServerDetectorLookup() {
        return osgiDetectorLookup;
    }

    /**
     * Create a log handler which tracks a {@link LogService} and, if available, use the log service
     * for logging, in the other time uses the servlet's default logging facility
     *
     * @param pServletConfig  servlet configuration
     * @param pConfiguration configuration which contains information like a log class handler or whether to to do
     *                       debugging
     */
    @Override
    protected LogHandler createLogHandler(ServletConfig pServletConfig, Configuration pConfiguration) {
        // If there is a bundle context available, set up a tracker for tracking the logging
        // service
        BundleContext ctx = getBundleContext(pServletConfig);
        if (ctx != null) {
            // Track logging service
            logTracker = new ServiceTracker<>(ctx, LogService.class.getName(), null);
            logTracker.open();
            return new ActivatorLogHandler(logTracker,Boolean.parseBoolean(pConfiguration.getConfig(ConfigKey.DEBUG)));
        } else {
            // Use default log handler
            return super.createLogHandler(pServletConfig,pConfiguration);
        }
    }

    /**
     * Initialize the service manager and add an OSGi based lookup mechanism
     *
     * @param pServletConfig servlet configuration
     * @param pServiceManager service manager to which to add services
     */
    @Override
    protected void initServices(ServletConfig pServletConfig, JolokiaServiceManager pServiceManager) {
        //super.initServices(pServletConfig, pServiceManager);
        BundleContext ctx = getBundleContext(pServletConfig);
        if (ctx != null) {
            pServiceManager.addServiceLookup(new OsgiJolokiaServiceFactory(ctx));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        if (logTracker != null) {
            logTracker.close();
            logTracker = null;
        }
        bundleContextGiven = null;
        super.destroy();
    }

    private BundleContext getBundleContext(ServletConfig pServletConfig) {
        // If no bundle context was provided, we are looking up the servlet context
        // for the bundle context, which will be available usually in a web extender
        if (bundleContextGiven == null) {
            // try to lookup bundle context from the servlet context
            ServletContext servletContext = pServletConfig.getServletContext();
            return (BundleContext) servletContext.getAttribute("osgi-bundlecontext");
        } else {
            return bundleContextGiven;
        }

    }

    // LogHandler which logs to a LogService if available, otherwise
    // it uses simply the servlets log facility
    private final class ActivatorLogHandler implements LogHandler {

        private final ServiceTracker<LogService, LogService> logTracker;
        private final boolean debug;

        private ActivatorLogHandler(ServiceTracker<LogService, LogService> pLogTracker, boolean pDebug) {
            logTracker = pLogTracker;
            debug = pDebug;
        }

        /** {@inheritDoc} */
        public void debug(String message) {
            doLog(LogService.LOG_DEBUG, message);
        }

        /** {@inheritDoc} */
        public void info(String message) {
            doLog(LogService.LOG_INFO, message);
        }

        private void doLog(int level, String message) {
            LogService logService = logTracker.getService();
            if (logService != null) {
                logService.log(level,message);
            } else {
                if (level != LogService.LOG_DEBUG || debug) {
                    log(message);
                }
            }
        }

        /** {@inheritDoc} */
        public void error(String message, Throwable t) {
            LogService logService = logTracker.getService();
            if (logService != null) {
                logService.log(LogService.LOG_ERROR,message,t);
            } else {
                log(message,t);
            }
        }

        /** {@inheritDoc} */
        public boolean isDebug() {
            return debug;
        }
    }

}
