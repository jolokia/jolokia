package org.jolokia.osgi.restrictor;

import org.jolokia.restrictor.Restrictor;
import org.osgi.framework.*;

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

/**
 * Activator for registering a sample {@link  Restrictor} as an OSGi service
 *
 * @author roland
 * @since 22.03.11
 */
public class RestrictorSampleActivator implements BundleActivator {

    private ServiceRegistration registration;

    public void start(BundleContext context) throws Exception {
        registration = context.registerService(Restrictor.class.getName(),new SampleRestrictor("java.lang"),null);
        System.out.println("Register sample restrictor service");
    }

    public void stop(BundleContext context) throws Exception {
        registration.unregister();
        System.out.println("Unregistered sample restrictor service");
    }
}
