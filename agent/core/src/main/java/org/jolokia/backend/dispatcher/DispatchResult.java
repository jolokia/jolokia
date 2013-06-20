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

    /**
     * Create a dispatch result
     *
     * @param pRetValue the return value
     * @param pPathParts path parts which should be used to interprete the result
     */
    public DispatchResult(Object pRetValue, List<String> pPathParts) {
        value = pRetValue;
        pathParts = pPathParts;
    }

    /**
     * Get the return value
     * @return return value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Path parts for interpreting the result
     * @return the path parts
     */
    public List<String> getPathParts() {
        return pathParts;
    }
}
