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

package org.jolokia.server.core.service.impl;

import java.util.*;

import org.jolokia.server.core.service.api.*;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Service Factory for tracking OSGi Jolokia Services
 *
 * @author roland
 * @since 19.06.13
 */
public class OsgiJolokiaServiceFactory implements JolokiaServiceLookup {

    // OSGi bundle context
    private final BundleContext context;

    // Map holding all service trackers for the various services
    private final Map<Class<? extends JolokiaService<?>>,ServiceTracker<JolokiaService<?>, JolokiaService<?>>> serviceTrackerMap;

    // Jolokia context
    private JolokiaContext jolokiaContext;

    /**
     * A new factory associated with the given context
     *
     * @param pCtx the OSGi context
     */
    public OsgiJolokiaServiceFactory(BundleContext pCtx) {
        context = pCtx;
        serviceTrackerMap = new HashMap<>();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    public <T extends JolokiaService<?>> Set<T> getServices(Class<T> pType) {
        ServiceTracker<?, ?> tracker = getServiceTracker(pType);

        Object[] services = tracker.getServices();
        if (services != null) {
            Set<T> ret = new TreeSet<>();
            for (Object service : services) {
                ret.add((T) service);
            }
            return ret;
        } else {
            return Collections.emptySet();
        }
    }

    private <T extends JolokiaService<?>> ServiceTracker<JolokiaService<?>, JolokiaService<?>> getServiceTracker(Class<T> pType) {
        ServiceTracker<JolokiaService<?>, JolokiaService<?>> tracker = serviceTrackerMap.get(pType);
        if (tracker == null) {
            tracker = initTracker(pType);
        }
        return tracker;
    }

    private <T extends JolokiaService<?>> ServiceTracker<JolokiaService<?>, JolokiaService<?>> initTracker(Class<T> pType) {
        ServiceTracker<JolokiaService<?>, JolokiaService<?>> tracker;// Tracker are initialized lazily because the JolokiaServiceManager must be initialized
        // before a service is looked up via the tracker because of the customizer calling
        // service lifecycle methods. getServices() is guaranteed to be called
        // only after the JolokiaContext has been setup.
        tracker = new ServiceTracker<>(context, pType.getName(), new JolokiaServiceTrackerCustomizer());
        serviceTrackerMap.put(pType,tracker);
        tracker.open();
        return tracker;
    }

    /** {@inheritDoc} */
    public void init(JolokiaContext pJolokiaContext) {
        jolokiaContext = pJolokiaContext;
        // The Init Tracker are initialized here so that they get initialized as soon as they kick in
        initTracker(JolokiaService.class);
    }

    /**
     * Close down the factory by closing all existing service trackers
     */
    public void destroy() {
        for (ServiceTracker<?, ?> tracker : serviceTrackerMap.values()) {
            tracker.close();
        }
    }

    // ==========================================================================================================
    // Service initializer for calling init on all newly added services
    private class JolokiaServiceTrackerCustomizer implements ServiceTrackerCustomizer<JolokiaService<?>, JolokiaService<?>> {

        /** {@inheritDoc} */
        public JolokiaService<?> addingService(ServiceReference<JolokiaService<?>> reference) {
            JolokiaService<?> jolokiaService = context.getService(reference);
            if (jolokiaService != null) {
                if (jolokiaContext != null) {
                    jolokiaService.init(jolokiaContext);
                } else {
                    throw new ServiceException("Cannot initialize service \"" + jolokiaService + "\" since the JolokiaContext " +
                                               "is not yet initialized");
                }
            }
            return jolokiaService;
        }

        /** {@inheritDoc} */
        public void modifiedService(ServiceReference<JolokiaService<?>> reference, JolokiaService<?> service) {
        }

        /** {@inheritDoc} */
        public void removedService(ServiceReference<JolokiaService<?>> reference, JolokiaService<?> service) {
            try {
                @SuppressWarnings("UnnecessaryLocalVariable")
                JolokiaService<?> jolokiaService = service;
                context.ungetService(reference);
                jolokiaService.destroy();
            } catch (Exception e) {
                throw new ServiceException("destroy() on JolokiaService " + service + " failed" + e,e);
            }
        }
    }
}
