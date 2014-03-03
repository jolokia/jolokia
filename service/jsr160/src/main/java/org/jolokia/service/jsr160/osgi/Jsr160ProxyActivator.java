package org.jolokia.service.jsr160.osgi;

import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.service.jsr160.Jsr160RequestHandler;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Simple activator for creating a JMX request handler
 *
 * @author roland
 * @since 02.03.14
 */
public class Jsr160ProxyActivator implements BundleActivator {

    /** {@inheritDoc} */
    public void start(BundleContext context) throws Exception {
        context.registerService(RequestHandler.class.getName(),new Jsr160RequestHandler(100),null);
    }

    /** {@inheritDoc} */
    public void stop(BundleContext context) throws Exception { }
}
