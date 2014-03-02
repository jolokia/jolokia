package org.jolokia.service.serializer.osgi;

import org.jolokia.server.core.service.serializer.JmxSerializer;
import org.jolokia.service.serializer.Converters;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Simple activator for creating a serializer object
 *
 * @author roland
 * @since 02.03.14
 */
public class SerializerServiceActivator implements BundleActivator {

    /** {@inheritDoc} */
    public void start(BundleContext context) throws Exception {
        context.registerService(JmxSerializer.class.getName(),new Converters(),null);
    }

    /** {@inheritDoc} */
    public void stop(BundleContext context) throws Exception { }
}
