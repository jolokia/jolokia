package org.jolokia.support.jmx.osgi;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import org.jolokia.support.jmx.JolokiaMBeanServerHolder;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author roland
 * @since 04.03.14
 */
public class JmxSupportActivator implements BundleActivator {

    public void start(BundleContext context) throws Exception {
        MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
        JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(platformMBeanServer,new TrackingSerializer(context));
    }

    public void stop(BundleContext context) throws Exception {
        MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
        JolokiaMBeanServerHolder.unregisterJolokiaMBeanServerHolderMBean(platformMBeanServer);
    }
}
