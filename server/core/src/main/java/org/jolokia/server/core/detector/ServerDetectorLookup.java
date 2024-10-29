package org.jolokia.server.core.detector;

import java.util.SortedSet;

import org.jolokia.server.core.service.api.LogHandler;

/**
 * Interface for a lookup mechanism to find server detector
 * @author roland
 * @since 28.02.14
 */
public interface ServerDetectorLookup {

    /**
     * Lookup all server detector available and return a list
     * of all found detectors
     *
     * @return set of server detectors
     */
    default SortedSet<ServerDetector> lookup() {
        return lookup(null);
    }

    /**
     * Lookup all server detector available and return a list
     * of all found detectors
     *
     * @param logHandler
     * @return set of server detectors
     */
    SortedSet<ServerDetector> lookup(LogHandler logHandler);

}
