package org.jolokia.server.core.osgi;

import java.io.Serializable;
import java.util.*;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.core.api.LogHandler;
import org.osgi.framework.*;

/**
 * Lookup service detectors lookups as OSGi services and collects the detectors found there
 *
 * @author roland
 * @since 28.02.14
 */
public class DelegatingServerDetectorLookup implements ServerDetectorLookup, Serializable {

    private static final long serialVersionUID = 24L;

    private final BundleContext context;

    DelegatingServerDetectorLookup(BundleContext pCtx) {
        context = pCtx;
    }

    /** {@inheritDoc} */
    public SortedSet<ServerDetector> lookup(LogHandler logHandler) {
        SortedSet<ServerDetector> ret = new TreeSet<>();
        if (context != null) {
            try {
                ServiceReference<?>[] refs = context.getServiceReferences(ServerDetectorLookup.class.getName(), null);
                if (refs != null) {
                    for (ServiceReference<?>  ref : refs) {
                        ServerDetectorLookup detectorLookup = (ServerDetectorLookup) context.getService(ref);
                        try {
                            ret.addAll(detectorLookup.lookup(logHandler));
                        } finally {
                            context.ungetService(ref);
                        }
                    }
                    return ret;
                }
            } catch (InvalidSyntaxException e) {
                // Will not occur since we dont use a filter
            } catch (IllegalStateException e) {
                // Error during lookup, we continue nevertheless, since detectors are not crucial
            }
        }
        // Its empty ...
        return ret;
    }
}
