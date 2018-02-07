package org.jolokia.osgi.security;

import org.jolokia.osgi.util.LogHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
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

    // Possible authentication mode when looking up a authenticator as service
    static final String AUTHMODE_SERVICE_ALL = "service-all";
    static final String AUTHMODE_SERVICE_ANY = "service-any";

    private final Set<Authenticator> authenticators = new HashSet<Authenticator>();

    private ServiceTracker authenticatorServiceTracker;

    // whether a single authenticator is sufficient to succeed
    private final boolean checkModeAny;

    public ServiceAuthenticationHttpContext(final BundleContext bundleContext, final String authMode) {
        if (!shouldBeUsed(authMode)) {
            throw new IllegalArgumentException(String.format("Internal: Invalid authMode %s given", authMode));
        }
        checkModeAny = authMode.equalsIgnoreCase(AUTHMODE_SERVICE_ANY);
        authenticatorServiceTracker =
            new ServiceTracker(bundleContext, Authenticator.class.getName(),
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
                boolean authenticated = authenticator.authenticate(request);
                if (checkModeAny && authenticated) {
                    // One successful authenticator is good enough
                    return true;
                }
                else if (!checkModeAny && !authenticated) {
                    // All must succeed, so any negative respond will kill the authentication
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return false;
                }
            }
            // if checkModeAny: Not a single succeeded, if checkModeAll: All have succeeded
            return !checkModeAny;
        }
    }

    public void close() {
        if (authenticatorServiceTracker != null) {
            authenticatorServiceTracker.close();
            authenticatorServiceTracker = null;
        }
    }

    /**
     * Check whether for the given authmode an instance of this context should be used (i.e. when the
     * auth-mode is "service-any" or "service-all"
     *
     * @param authMode authmode to check
     * @return true if this context should be used for the agent, false otherwise
     */
    public static boolean shouldBeUsed(String authMode) {
        return authMode != null &&
               (authMode.equalsIgnoreCase(AUTHMODE_SERVICE_ALL) || authMode.equalsIgnoreCase(AUTHMODE_SERVICE_ANY));
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
                LogHelper.logError("Unable to use provided Authenticator", e);
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

    }
}