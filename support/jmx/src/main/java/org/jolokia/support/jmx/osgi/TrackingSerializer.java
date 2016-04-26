package org.jolokia.support.jmx.osgi;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.service.serializer.WriteRequestValues;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A delegating serializer which track a {@link Serializer} service and delegates to this, if available.
 * If not available, an exception is thrown when a serializer method is called.
 *
 * @author roland
 * @since 04.03.14
 */
public class TrackingSerializer extends AbstractJolokiaService<Serializer> implements Serializer {

    // tracks the service
    private final ServiceTracker tracker;

    /**
     * Proxy using the given context for tracking serializer
     *
     * @param pContext central OSGi context
     */
    TrackingSerializer(BundleContext pContext) {
        super(Serializer.class,0);
        tracker = new ServiceTracker(pContext,Serializer.class.getName(),null);
    }

    // Get the delegated service via the tracker
    private Serializer getDelegate() {
        Serializer delegate = (Serializer) tracker.getService();
        if (delegate == null) {
            throw new IllegalStateException("No serializer available");
        }
        return delegate;
    }

    /** {@inheritDoc} */
    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions) throws AttributeNotFoundException {
        return getDelegate().serialize(pValue,pPathParts,pOptions);
    }

    /** {@inheritDoc} */
    public Object deserialize(String pExpectedClassName, Object pValue) {
        return getDelegate().deserialize(pExpectedClassName,pValue);
    }

    /** {@inheritDoc} */
    public WriteRequestValues setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts) throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        return getDelegate().setInnerValue(pOuterObject, pNewValue, pPathParts);
    }

    /** {@inheritDoc} */
    public Object deserializeOpenType(OpenType<?> pOpenType, Object pValue) {
        return getDelegate().deserializeOpenType(pOpenType,pValue);
    }
}
