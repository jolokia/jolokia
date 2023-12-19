package org.jolokia.service.jmx.osgi;

import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.service.jmx.LocalRequestHandler;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Simple activator for creating a JMX request handler
 *
 * @author roland
 * @since 02.03.14
 */
public class JmxServiceActivator implements BundleActivator {

    /** {@inheritDoc} */
    public void start(BundleContext context) throws Exception {
        context.registerService(RequestHandler.class.getName(),new LocalRequestHandler(1000),null);
    }

    /** {@inheritDoc} */
    public void stop(BundleContext context) throws Exception { }
}
