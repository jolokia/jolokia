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

import org.jolokia.service.*;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Service Factory for tracking OSGi Jolokia Services
 * TODO: Initialize services when they come in
 * @author roland
 * @since 19.06.13
 */
public class OsgiJolokiaServiceFactory implements JolokiaServiceLookup {

    // OSGi bundle context
    private final BundleContext context;

    // Map holding all service trackers for the various services
    private Map<Class<? extends JolokiaService>,ServiceTracker> serviceTrackerMap;

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
            tracker = new ServiceTracker(context,pType.getName(),null);
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
    public void init(JolokiaContext pJolokiaContext) { }

    /**
     * Close down the factory by closing all existing service trackers
     */
    public void destroy() {
        for (ServiceTracker tracker : serviceTrackerMap.values()) {
            tracker.close();
        }
    }
}
