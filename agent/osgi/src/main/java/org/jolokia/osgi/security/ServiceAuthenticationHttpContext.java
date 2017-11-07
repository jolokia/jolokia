package org.jolokia.osgi.security;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/*
 * Copyright 2017 Ryan Goulding
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

/**
 * Authentication context based on an Authenticator Service.
 */
public class ServiceAuthenticationHttpContext extends DefaultHttpContext {

    private volatile Set<Authenticator> authenticators = new HashSet();

    private ServiceTracker authenticatorServiceTracker;

    public ServiceAuthenticationHttpContext(final BundleContext bundleContext) {
        authenticatorServiceTracker = new ServiceTracker(bundleContext, Authenticator.class.getName(),
                new AuthenticatorServiceCustomizer(bundleContext));
        authenticatorServiceTracker.open();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleSecurity(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {

        synchronized(authenticators) {
            // deny access if authMode is set to service but a service is not provided
            if (authenticators.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            for (final Authenticator authenticator : authenticators) {
                if (!authenticator.authenticate(request)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return false;
                }
            }
            return true;
        }
    }

    public void close() {
        if (authenticatorServiceTracker != null) {
            authenticatorServiceTracker.close();
            authenticatorServiceTracker = null;
        }
    }


    // =============================================================================

    private class AuthenticatorServiceCustomizer implements ServiceTrackerCustomizer {

        private final BundleContext bundleContext;

        AuthenticatorServiceCustomizer(final BundleContext bundleContext) {
            this.bundleContext = bundleContext;
        }

        /**
         * {@inheritDoc}
         */
        public Object addingService(ServiceReference serviceReference) {
            final Object service = bundleContext.getService(serviceReference);
            try {
                synchronized (authenticators) {
                    authenticators.add((Authenticator) service);
                    return authenticators;
                }
            } catch (final ClassCastException e) {
                logError("Unable to use provided Authenticator", e);
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public void modifiedService(final ServiceReference serviceReference, final Object service) {
        }

        /**
         * {@inheritDoc}
         */
        public void removedService(final ServiceReference serviceReference, final Object service) {
            synchronized (authenticators) {
                bundleContext.ungetService(serviceReference);
                authenticators.remove(service);
            }
        }

        @SuppressWarnings("PMD.SystemPrintln")
        private void logError(final String message, final Throwable throwable) {
            final BundleContext bundleContext = FrameworkUtil
                    .getBundle(ServiceAuthenticationHttpContext.class)
                    .getBundleContext();
            final ServiceReference lRef = bundleContext.getServiceReference(LogService.class.getName());
            if (lRef != null) {
                try {
                    final LogService logService = (LogService) bundleContext.getService(lRef);
                    if (logService != null) {
                        logService.log(LogService.LOG_ERROR, message, throwable);
                        return;
                    }
                } finally {
                    bundleContext.ungetService(lRef);
                }
            }
            System.err.println("Jolokia-Error: " + message + " : " + throwable.getMessage());
        }
    }
}