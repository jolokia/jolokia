package org.jolokia.server.core.service.impl;

import java.util.*;

import org.jolokia.server.core.util.LocalServiceFactory;
import org.jolokia.server.core.detector.*;

/**
 * Classpath scanner for detectors
 *
 * @author roland
 * @since 28.02.14
 */
public class ClasspathServerDetectorLookup implements ServerDetectorLookup {

    /** {@inheritDoc} */
    public SortedSet<ServerDetector> lookup() {
        SortedSet<ServerDetector> detectors = new TreeSet<>(
                LocalServiceFactory.createServices("META-INF/jolokia/detectors-default",
                                                   "META-INF/jolokia/detectors"));
        detectors.add(ServerDetector.FALLBACK);
        return detectors;
    }

}
