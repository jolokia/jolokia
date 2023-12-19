package org.jolokia.service.discovery.osgi;

import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.service.discovery.DiscoveryMulticastResponder;
import org.jolokia.service.discovery.JolokiaDiscovery;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Simple activator for creating a JMX request handler. Note that the lifecycle of the created
 * beans (i.e. init() and destroy()) is handled by the {@link JolokiaServiceManager}, the OSGi lifecycle
 * is only used for creating the service objects (but no other lifecycle stuff).
 *
 * @author roland
 * @since 02.03.14
 */
public class DiscoveryServiceActivator implements BundleActivator {

    /** {@inheritDoc} */
    public void start(BundleContext context) throws Exception {
        context.registerService(JolokiaService.class.getName(),new JolokiaDiscovery(0),null);
        context.registerService(JolokiaService.class.getName(),new DiscoveryMulticastResponder(),null);
    }

    /** {@inheritDoc} */
    public void stop(BundleContext context) throws Exception { }
}
