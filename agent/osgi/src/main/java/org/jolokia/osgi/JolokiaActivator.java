package org.jolokia.osgi;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.jolokia.ConfigKey;
import org.jolokia.http.AgentServlet;
import org.jolokia.LogHandler;
import org.osgi.framework.*;
import org.osgi.service.http.*;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import static org.jolokia.ConfigKey.*;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * OSGi Activator for the Jolokia Agent
 *
 * @author roland
 * @since Dec 27, 2009
 */
public class JolokiaActivator implements BundleActivator {

    // Context associated with this activator
    private BundleContext bundleContext;

    // Thread-Locals which will be used for holding the bundle context and
    // the https service during initialization
    private static final ThreadLocal<BundleContext> bundleContextThreadLocal = new ThreadLocal<BundleContext>();

    // Tracker to be used for the LogService
    private ServiceTracker logTracker;

    // Tracker for HttpService
    private ServiceTracker httpServiceTracker;

    // Prefix used for configuration values
    private static final String CONFIG_PREFIX = "org.jolokia";

    // Our own log handler
    private LogHandler logHandler;

    public void start(BundleContext pBundleContext) {
        bundleContext = pBundleContext;

        // Track logging service
        logTracker = new ServiceTracker(pBundleContext, LogService.class.getName(), null);
        logTracker.open();
        logHandler = new ActivatorLogHandler(logTracker);

        // Track HttpService
        httpServiceTracker = new ServiceTracker(pBundleContext,HttpService.class.getName(), new HttpServiceCustomizer(pBundleContext));
        httpServiceTracker.open();
    }

    public void stop(BundleContext pBundleContext) {
        assert pBundleContext.equals(bundleContext);

        logTracker.close();
        logTracker = null;
        logHandler = null;
        httpServiceTracker.close();
        httpServiceTracker = null;

        bundleContext = null;
    }

    /**
     * Get the security context for out servlet. Dependend on the configuration,
     * this is either a no-op context or one which authenticates with a given used
     *
     * @return the HttpContext with which the agent servlet gets registered.
     */
    public HttpContext getHttpContext() {
        final String user = getConfiguration(USER);
        final String password = getConfiguration(PASSWORD);
        if (user == null) {
            return new JolokiaHttpContext();
        } else {
            return new JolokiaAuthenticatedHttpContext(user, password);
        }
    }

    /**
     * Get the servlet alias under which the agen servlet is registered
     * @return get the servlet alias
     */
    public String getServletAlias() {
        return getConfiguration(AGENT_CONTEXT);
    }

    /**
     * Get the current bundle context. This static method can be used during startup
     * of the agent servlet. At other times, this method will return null
     *
     * @return the current bundle context during adding of a HttpService, null at other
     *         times
     */
    public static BundleContext getCurrentBundleContext() {
        return bundleContextThreadLocal.get();
    }

    // ==================================================================================

    // Customizer for registering servlet at a HttpService

    private AgentServlet createServlet(LogHandler pLogHandler) {
        return new AgentServlet(pLogHandler);
    }

    private Dictionary<String,String> getConfig() {
        Dictionary<String,String> config = new Hashtable<String,String>();
        for (ConfigKey key : ConfigKey.values()) {
            String value = getConfiguration(key);
            if (value != null) {
                config.put(key.getKeyValue(),value);
            }
        }
        return config;
    }

    private String getConfiguration(ConfigKey pKey) {
        // TODO: Use fragments and/or configuration service if available.
        String value = bundleContext.getProperty(CONFIG_PREFIX + "." + pKey);
        if (value == null) {
            value = pKey.getDefaultValue();
        }
        return value;
    }

    // =============================================================================

    private class HttpServiceCustomizer implements ServiceTrackerCustomizer {
        private final BundleContext context;

        public HttpServiceCustomizer(BundleContext pContext) {
            context = pContext;
        }

        public Object addingService(ServiceReference reference) {
            HttpService service = (HttpService) context.getService(reference);
            bundleContextThreadLocal.set(context);
            try {
                service.registerServlet(getServletAlias(),
                                        createServlet(logHandler),
                                        getConfig(),
                                        getHttpContext());
            } catch (ServletException e) {
                logHandler.error("Servlet Exception: " + e,e);
            } catch (NamespaceException e) {
                logHandler.error("Namespace Exception: " + e,e);
            } finally {
                bundleContextThreadLocal.remove();
            }
            return service;
        }

        public void modifiedService(ServiceReference reference, Object service) {
        }

        public void removedService(ServiceReference reference, Object service) {
            HttpService httpService = (HttpService) service;
            httpService.unregister(getServletAlias());
        }
    }

    private static final class ActivatorLogHandler implements LogHandler {

        private ServiceTracker logTracker;

        private ActivatorLogHandler(ServiceTracker pLogTracker) {
            logTracker = pLogTracker;
        }

        public void debug(String message) {
            log(LogService.LOG_DEBUG,message);
        }

        public void info(String message) {
            log(LogService.LOG_INFO,message);
        }

        private void log(int level,String message) {
            LogService logService = (LogService) logTracker.getService();
            if (logService != null) {
                logService.log(level,message);
            }
        }

        public void error(String message, Throwable t) {
            LogService logService = (LogService) logTracker.getService();
            if (logService != null) {
                logService.log(LogService.LOG_ERROR,message,t);
            }
        }
    }

}
