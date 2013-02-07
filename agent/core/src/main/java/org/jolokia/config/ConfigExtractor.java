package org.jolokia.config;

import java.util.Enumeration;

/**
 * Interface used for extracting configuration from various backend
 * configuration like SevletContext or servlet config
 * @author roland
 * @since 07.02.13
 */
public interface ConfigExtractor {
    /**
     * Get all configuration name
     * @return enumeration of config names
     */
    Enumeration getNames();

    /**
     * Get the parameter for a certain
     * @param pKeyS string representation of the config key to fetch
     * @return the value of the configuration parameter or <code>null</code> if no such parameter exists
     */
    String getParameter(String pKeyS);
}
