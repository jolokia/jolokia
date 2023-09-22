package org.jolokia.osgi.bundle;

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

import org.apache.felix.http.jetty.internal.JettyActivator;
import org.jolokia.osgi.JolokiaActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Activator for activation the embedded jolokia agent as well
 * as the Aries JMX bundle. So it's an aggregat activator.
 *
 * It also registers an (arbitrary) MBeanServer if not already
 * an MBeanServer is registered. This service is required by Aries JMX.

 * @author roland
 * @since Jan 9, 2010
 */
public class Activator implements BundleActivator {

    // Jolokia Activator
    private JolokiaActivator jolokiaActivator;

    // Felix Jetty Activator
    private JettyActivator felixHttpWebActivator;

    /**
     * No-arg constructor
     */
    public Activator() {
        jolokiaActivator = new JolokiaActivator();
        felixHttpWebActivator = new JettyActivator();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void start(BundleContext pContext) throws Exception {
        felixHttpWebActivator.start(pContext);
        jolokiaActivator.start(pContext);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void stop(BundleContext pContext) throws Exception {
        jolokiaActivator.stop(pContext);
        felixHttpWebActivator.stop(pContext);
    }

}
