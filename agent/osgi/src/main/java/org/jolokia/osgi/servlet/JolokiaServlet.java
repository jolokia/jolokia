/*
 * Copyright 2009-2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.osgi.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.jolokia.LogHandler;
import org.jolokia.http.AgentServlet;
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
 * the log service (if availabled). Otherwise it uses
 * the servlet's logging facility as fallback.
 *
 * @author roland
 * @since 10.02.11
 */
public class JolokiaServlet extends AgentServlet {

    private static final long serialVersionUID = 23L;

    private BundleContext bundleContext;

    // Tracker to be used for the LogService
    private ServiceTracker logTracker;

    // Thread-Locals which will be used for holding the bundle context and
    // the https service during initialization
    private static final ThreadLocal<BundleContext> BUNDLE_CONTEXT_THREAD_LOCAL = new ThreadLocal<BundleContext>();

    public JolokiaServlet() {
        this(null);
    }

    public JolokiaServlet(BundleContext pContext) {
        bundleContext = pContext;
        if (bundleContext != null) {
            // Track logging service
            logTracker = new ServiceTracker(bundleContext, LogService.class.getName(), null);
            logTracker.open();
            setLogHandler(new ActivatorLogHandler(logTracker));
        }
    }

    @Override
    public void init(ServletConfig pConfig) throws ServletException {
        // We are making the bundle context available here as a thread local
        // so that the server detector has access to the bundle in order to detect
        // the Osgi-Environment
        BUNDLE_CONTEXT_THREAD_LOCAL.set(bundleContext);
        try {
            super.init(pConfig);
        } finally {
            BUNDLE_CONTEXT_THREAD_LOCAL.remove();
        }
    }

    @Override
    public void destroy() {
        if (logTracker != null) {
            logTracker.close();
            logTracker = null;
        }
        bundleContext = null;
        super.destroy();
    }

    /**
     * Get the current bundle context. This static method can be used during startup
     * of the agent servlet. At other times, this method will return null
     *
     * @return the current bundle context during adding of a HttpService, null at other
     *         times
     */
    public static BundleContext getCurrentBundleContext() {
        return BUNDLE_CONTEXT_THREAD_LOCAL.get();
    }


    // LogHandler which logs to a LogService if available, otherwise
    // it uses simply the servlets log facility
    private final class ActivatorLogHandler implements LogHandler {

        private ServiceTracker logTracker;

        private ActivatorLogHandler(ServiceTracker pLogTracker) {
            logTracker = pLogTracker;
        }

        public void debug(String message) {
            doLog(LogService.LOG_DEBUG, message);
        }

        public void info(String message) {
            doLog(LogService.LOG_INFO, message);
        }

        private void doLog(int level, String message) {
            LogService logService = (LogService) logTracker.getService();
            if (logService != null) {
                logService.log(level,message);
            } else {
                log(message);
            }
        }

        public void error(String message, Throwable t) {
            LogService logService = (LogService) logTracker.getService();
            if (logService != null) {
                logService.log(LogService.LOG_ERROR,message,t);
            } else {
                log(message,t);
            }
        }
    }

}
