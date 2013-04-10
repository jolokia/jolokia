package org.jolokia.backend;

/**
 * @author roland
 * @since 09.04.13
 */
public interface DebugStoreMBean {

    /**
     * Get latest debug information if debugging is switched on. The returned output
     * will not take more than {@link #getMaxDebugEntries()} lines.
     *
     * @return debug info in plain ascii.
     */
    String debugInfo();

    /**
     * Reset all debug information stored internally
     */
    void resetDebugInfo();

    /**
     * Check, whether debugging is switched on
     * @return state of debugging
     */
    boolean isDebug();

    /**
     * Set debugging to given state
     * @param pSwitch true, if debugging should be switched on, false otherwise
     */
    void setDebug(boolean pSwitch);

    /**
     * Number of debug entries to remember
     *
     * @return number of debug entries
     */
    int getMaxDebugEntries();

    /**
     * Set the number of debugging info to remember
     * @param pNumber entries to set
     */
    void setMaxDebugEntries(int pNumber);
}
