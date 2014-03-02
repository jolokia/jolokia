package org.jolokia.agent.osgi.servlet;

import java.io.Serializable;
import java.util.*;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.osgi.framework.*;

/**
 * Lookup service detectors as OSGi services
 * @author roland
 * @since 28.02.14
 */
public class OsgiServerDetectorLookup implements ServerDetectorLookup, Serializable {

    private static final long serialVersionUID = 24L;

    private final BundleContext context;

    OsgiServerDetectorLookup(BundleContext pCtx) {
        context = pCtx;
    }

    /** {@inheritDoc} */
    public List<ServerDetector> lookup() {
        if (context != null) {
            try {
                ServiceReference[] refs = context.getServiceReferences(ServerDetector.class.getName(), null);
                if (refs != null) {
                    List<ServerDetector> ret = new ArrayList<ServerDetector>();
                    for (ServiceReference ref : refs) {
                        ret.add((ServerDetector) context.getService(ref));
                    }
                    return ret;
                }
            } catch (InvalidSyntaxException e) {
                // Will not occur since we dont use a filter
                return Collections.emptyList();
            } catch (IllegalStateException e) {
                // Error during lookup, we continue nevertheless, since detectors are not crucial
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();

    }
}
