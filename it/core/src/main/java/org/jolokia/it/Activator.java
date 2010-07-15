package org.jolokia.it;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author roland
 * @since Mar 27, 2010
 */
public class Activator implements BundleActivator {
    private ItSetup itSetup;

    public Activator() {
        itSetup = new ItSetup();
    }

    public void start(BundleContext context) {
        itSetup.start();
    }

    public void stop(BundleContext context) {
        itSetup.stop();
    }
}
