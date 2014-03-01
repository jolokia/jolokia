package org.jolokia.server.core.detector;

import java.util.List;

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
     * @return list of server detectors
     */
    List<ServerDetector> lookup();
}
