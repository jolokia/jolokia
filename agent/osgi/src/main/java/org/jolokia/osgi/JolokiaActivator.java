package org.jolokia.osgi;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.jolokia.ConfigKey;
import org.jolokia.osgi.servlet.JolokiaContext;
import org.jolokia.osgi.servlet.JolokiaServlet;
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
public class JolokiaActivator implements BundleActivator, JolokiaContext {

    // Context associated with this activator
    private BundleContext bundleContext;

    // Tracker for HttpService
    private ServiceTracker httpServiceTracker;

    // Prefix used for configuration values
    private static final String CONFIG_PREFIX = "org.jolokia";

    // HttpContext used for authorization
    private HttpContext jolokiaHttpContext;

    // Registration object for this JolokiaContext
    private ServiceRegistration jolokiaServiceRegistration;

    public void start(BundleContext pBundleContext) {
        bundleContext = pBundleContext;

        // Track HttpService
        httpServiceTracker = new ServiceTracker(pBundleContext,HttpService.class.getName(), new HttpServiceCustomizer(pBundleContext));
        httpServiceTracker.open();

        // Register us as JolokiaContext
        jolokiaServiceRegistration = pBundleContext.registerService(JolokiaContext.class.getCanonicalName(),this,null);
    }

    public void stop(BundleContext pBundleContext) {
        assert pBundleContext.equals(bundleContext);

        httpServiceTracker.close();
        httpServiceTracker = null;

        jolokiaServiceRegistration.unregister();
        jolokiaServiceRegistration = null;
        bundleContext = null;
    }

    /**
     * Get the security context for out servlet. Dependend on the configuration,
     * this is either a no-op context or one which authenticates with a given user
     *
     * @return the HttpContext with which the agent servlet gets registered.
     */
    public synchronized HttpContext getHttpContext() {
        if (jolokiaHttpContext == null) {
            final String user = getConfiguration(USER);
            final String password = getConfiguration(PASSWORD);
            if (user == null) {
                jolokiaHttpContext = new JolokiaHttpContext();
            } else {
                jolokiaHttpContext = new JolokiaAuthenticatedHttpContext(user, password);
            }
        }
        return jolokiaHttpContext;
    }

    /**
     * Get the servlet alias under which the agen servlet is registered
     * @return get the servlet alias
     */
    public String getServletAlias() {
        return getConfiguration(AGENT_CONTEXT);
    }


    // ==================================================================================

    // Customizer for registering servlet at a HttpService

    protected Dictionary<String,String> getConfiguration() {
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
            try {
                service.registerServlet(getServletAlias(),
                                        new JolokiaServlet(context),
                                        getConfiguration(),
                                        getHttpContext());
            } catch (ServletException e) {
                logError("Servlet Exception: " + e, e);
            } catch (NamespaceException e) {
                logError("Namespace Exception: " + e, e);
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

    @SuppressWarnings("PMD.SystemPrintln")
    private void logError(String message,Throwable throwable) {
        ServiceReference lRef = bundleContext.getServiceReference(LogService.class.getName());
        if (lRef != null) {
            try {
                LogService logService = (LogService) bundleContext.getService(lRef);
                if (logService != null) {
                    logService.log(LogService.LOG_ERROR,message,throwable);
                    return;
                }
            } finally {
                bundleContext.ungetService(lRef);
            }
        }
        System.err.println("Jolokia-Error: " + message + " : " + throwable.getMessage());
    }


}
