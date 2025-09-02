package org.jolokia.server.core.config;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;
import java.util.Set;

/**
 * Interface for accessing Jolokia configuration.
 *
 * @author roland
 * @since 13.04.13
 */
public interface Configuration {

    /**
     * Get a configuration value by {@link ConfigKey}. The order of lookup is:<ol>
     *     <li>environment variables prefixed with {@code JOLOKIA_} - not resolved</li>
     *     <li>system properties prefixed with {@code jolokia.} - not resolved</li>
     *     <li>servlet config parameters for Servlet Agent - resolved</li>
     *     <li>servlet context parameters for Servlet Agent - resolved</li>
     *     <li>options from JVM Agent command line - resolved</li>
     *     <li>options from {@code config} file for JVM Agent - resolved</li>
     *     <li>options from {@code /default-jolokia-agent.properties} for JVM Agent - not resolved</li>
     *     <li>default values from {@link ConfigKey#getDefaultValue()} - not resolved</li>
     * </ol>
     *
     * @param pKey the configuration key to lookup
     * @return the configuration value or the default value if no configuration
     *         was given.
     */
    String getConfig(ConfigKey pKey);

    /**
     * Get all keys stored in this configuration
     */
    Set<ConfigKey> getConfigKeys();

    /**
     * Check whether the given configuration holds a value for the given key
     * @param pKey key to check
     * @return true if the configuration has this key
     */
    boolean containsKey(ConfigKey pKey);

    /**
     * Get Network configuration - mapping of network properties to ip addresses and host names.
     * @return
     */
    Map<String, String> getNetworkConfig();

}
