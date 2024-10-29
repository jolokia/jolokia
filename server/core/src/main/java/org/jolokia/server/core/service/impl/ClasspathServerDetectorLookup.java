package org.jolokia.server.core.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.util.LocalServiceFactory;

/**
 * Classpath scanner for detectors
 *
 * @author roland
 * @since 28.02.14
 */
public class ClasspathServerDetectorLookup implements ServerDetectorLookup {

    /** {@inheritDoc} */
    public SortedSet<ServerDetector> lookup(LogHandler logHandler) {
        List<ServerDetector> services = LocalServiceFactory.createServices("META-INF/jolokia/detectors-default",
            "META-INF/jolokia/detectors");

        services.add(ServerDetector.FALLBACK);
        if (!LocalServiceFactory.validateServices(services, logHandler)) {
            return new TreeSet<>(Collections.singleton(ServerDetector.FALLBACK));
        }

        return new TreeSet<>(services);
    }

}
