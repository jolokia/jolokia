package org.jolokia.server.detector.osgi;

import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi Activator for registering a detector lookup as service
 *
 * @author roland
 * @since 03.03.14
 */
public class DetectorActivator implements BundleActivator {

    public void start(BundleContext pContext) throws Exception {
        pContext.registerService(ServerDetectorLookup.class.getName(),
                                 new OsgiServerDetectorLookup(pContext),
                                 null);
    }

    public void stop(BundleContext context) throws Exception {

    }
}
