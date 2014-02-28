package org.jolokia.core.detector;

/**
 * Information about the the server product the agent is running in.
 *
 * @author roland
 * @since 05.11.10
 */
public interface ServerHandle {

    /**
     * Get name of vendor
     */
    String getVendor();

    /**
     * Get the name of the server this agent is running in
     *
     * @return server name
     */
    String getProduct();

    /**
     * Get version number of the agent server
     * @return version number
     */
    String getVersion();

    // =============================================================================================

    // A "null" server handle
    final ServerHandle NULL_SERVER_HANDLE = new ServerHandle() {
        public String getVendor() {
            return null;
        }

        public String getProduct() {
            return null;
        }

        public String getVersion() {
            return null;
        }
    };
}
