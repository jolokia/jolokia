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

package org.jolokia.osgi.servlet;

import java.util.*;

import org.jolokia.core.service.*;
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
    private Map<Class<? extends JolokiaService>,ServiceTracker> serviceTrackerMap;

    // Jolokia context
    private JolokiaContext jolokiaContext;

    /**
     * A new factory associated with the given context
     *
     * @param pCtx the OSGi context
     */
    public OsgiJolokiaServiceFactory(BundleContext pCtx) {
        context = pCtx;
        serviceTrackerMap = new HashMap<Class<? extends JolokiaService>, ServiceTracker>();
    }

    /** {@inheritDoc} */
    public <T extends JolokiaService> Set<T> getServices(Class<T> pType) {
        ServiceTracker tracker = serviceTrackerMap.get(pType);
        if (tracker == null) {
            // Tracker are initialized lazily because the JolokiaServiceManager must be initialized
            // before a service is looked up via the tracker because of the customizer calling
            // service lifecycle methods. getServices() is guaranteed to be called
            // only after the JolokiaService has been setup.
            tracker = new ServiceTracker(context,pType.getName(), new JolokiaServiceTrackerCustomizer());
            serviceTrackerMap.put(pType,tracker);
            tracker.open();
        }

        Object services[] = tracker.getServices();
        if (services != null) {
            Set<T> ret = new TreeSet<T>();
            for (Object service : services) {
                ret.add((T) service);
            }
            return ret;
        } else {
            return Collections.emptySet();
        }
    }

    /** {@inheritDoc} */
    public void init(JolokiaContext pJolokiaContext) {
        jolokiaContext = pJolokiaContext;
    }

    /**
     * Close down the factory by closing all existing service trackers
     */
    public void destroy() {
        for (ServiceTracker tracker : serviceTrackerMap.values()) {
            tracker.close();
        }
    }

    // ==========================================================================================================
    // Service initializer for calling init on all newly added services
    private  class JolokiaServiceTrackerCustomizer implements ServiceTrackerCustomizer {

        /** {@inheritDoc} */
        public Object addingService(ServiceReference reference) {
            JolokiaService jolokiaService = (JolokiaService) context.getService(reference);
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
        public void modifiedService(ServiceReference reference, Object service) {
        }

        /** {@inheritDoc} */
        public void removedService(ServiceReference reference, Object service) {
            try {
                JolokiaService jolokiaService = (JolokiaService) service;
                context.ungetService(reference);
                jolokiaService.destroy();
            } catch (Exception e) {
                throw new ServiceException("destroy() on JolokiaService " + service + " failed" + e,e);
            }
        }
    }
}
