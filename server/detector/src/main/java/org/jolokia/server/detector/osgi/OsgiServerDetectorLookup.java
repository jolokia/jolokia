package org.jolokia.server.detector.osgi;

import java.util.*;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.util.LocalServiceFactory;
import org.osgi.framework.BundleContext;

/**
 * Lookup class in order to provide a list of detectors for an OSGi environment
 *
 * @author roland
 * @since 03.03.14
 */
public class OsgiServerDetectorLookup implements ServerDetectorLookup {

    private final BundleContext context;

    OsgiServerDetectorLookup(BundleContext pContext) {
        context = pContext;
    }

    /** {@inheritDoc} */
    public SortedSet<ServerDetector> lookup(LogHandler logHandler) {
        SortedSet<ServerDetector> detectors = new TreeSet<>();

        detectors.addAll(classpathDetectors(logHandler));
        detectors.addAll(osgiDetectors());
        detectors.add(ServerDetector.FALLBACK);

        return detectors;
    }

    private List<ServerDetector> osgiDetectors() {
        return Arrays.asList(
                new VirgoDetector(context),
                new FelixDetector(context),
                new EquinoxDetector(context),
                new KnopflerfishDetector(context));
    }

    private List<ServerDetector> classpathDetectors(LogHandler logHandler) {
        List<ServerDetector> services = LocalServiceFactory.createServices(this.getClass().getClassLoader(),
            "META-INF/jolokia/detectors-default",
            "META-INF/jolokia/detectors");

        if (LocalServiceFactory.validateServices(services, logHandler)) {
            return services;
        }

        return Collections.emptyList();
    }
}
