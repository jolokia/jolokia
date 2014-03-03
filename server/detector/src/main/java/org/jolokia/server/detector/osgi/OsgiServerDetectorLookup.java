package org.jolokia.server.detector.osgi;

import java.util.*;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.util.LocalServiceFactory;
import org.osgi.framework.BundleContext;

/**
 * Lookup class in order to provide a list of detectors for an OSGi environment
 *
 * @author roland
 * @since 03.03.14
 */
public class OsgiServerDetectorLookup implements ServerDetectorLookup {

    private BundleContext context;

    OsgiServerDetectorLookup(BundleContext pContext) {
        context = pContext;
    }

    /** {@inheritDoc} */
    public SortedSet<ServerDetector> lookup() {
        SortedSet<ServerDetector> detectors = new TreeSet<ServerDetector>();

        detectors.addAll(classpathDetectors());
        detectors.addAll(osgiDetectors());
        detectors.add(ServerDetector.FALLBACK);

        return detectors;
    }

    private List<ServerDetector> osgiDetectors() {
        return Arrays.<ServerDetector>asList(
                new VirgoDetector(context),
                new FelixDetector(context),
                new EquinoxDetector(context),
                new KnopflerfishDetector(context));
    }

    private List<ServerDetector> classpathDetectors() {
        return LocalServiceFactory.createServices(this.getClass().getClassLoader(),
                                                  "META-INF/jolokia/detectors-default",
                                                  "META-INF/jolokia/detectors");
    }
}
