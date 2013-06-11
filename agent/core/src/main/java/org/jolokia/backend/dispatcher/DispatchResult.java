package org.jolokia.backend.dispatcher;

import java.util.List;

/**
 * The result of a request dispatching
 *
 * @author roland
 * @since 11.06.13
 */
public class DispatchResult {

    // The resulting value of the dispatch
    private final Object value;

    // Path parts to use for extracting the value
    private List<String> pathParts;

    public DispatchResult(Object pRetValue, List<String> pPathParts) {
        value = pRetValue;
        pathParts = pPathParts;
    }

    public Object getValue() {
        return value;
    }

    public List<String> getPathParts() {
        return pathParts;
    }
}
