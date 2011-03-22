package org.jolokia.osgi.restrictor;

import org.jolokia.restrictor.Restrictor;
import org.osgi.framework.*;

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
