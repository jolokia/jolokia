package org.jolokia.service.history.osgi;

import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.service.history.HistoryMBeanRequestInterceptor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Simple activator for creating a JMX request handler
 *
 * @author roland
 * @since 02.03.14
 */
public class HistoryServiceActivator implements BundleActivator {

    /** {@inheritDoc} */
    public void start(BundleContext context) throws Exception {
        context.registerService(new String[] {
                JolokiaService.class.getName(),
                RequestInterceptor.class.getName()
        },new HistoryMBeanRequestInterceptor(0),null);
    }

    /** {@inheritDoc} */
    public void stop(BundleContext context) throws Exception { }
}
