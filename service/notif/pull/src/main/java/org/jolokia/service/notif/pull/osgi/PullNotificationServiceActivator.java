package org.jolokia.service.notif.pull.osgi;

import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.notification.NotificationBackend;
import org.jolokia.service.notif.pull.PullNotificationBackend;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Simple activator for creating a JMX request handler
 *
 * @author roland
 * @since 02.03.14
 */
public class PullNotificationServiceActivator implements BundleActivator {

    /** {@inheritDoc} */
    public void start(BundleContext context) throws Exception {
        context.registerService(new String[] {
                JolokiaService.class.getName(),
                NotificationBackend.class.getName()
        },new PullNotificationBackend(0),null);
    }

    /** {@inheritDoc} */
    public void stop(BundleContext context) throws Exception { }
}
