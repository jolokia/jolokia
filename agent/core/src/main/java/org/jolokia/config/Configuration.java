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

package org.jolokia.config;

import java.util.Map;

/**
 * Interface for accessing Jolokia configuration.
 *
 * @author roland
 * @since 13.04.13
 */
public interface Configuration {

    /**
     * Get a configuration value if set as configuration or the default
     * value if not
     *
     * @param pKey the configuration key to lookup
     * @return the configuration value or the default value if no configuration
     *         was given.
     */
    String getConfig(ConfigKey pKey);

    /**
     * Get an configuration value as int value
     * @param pKey the configuration key
     * @return the value set or, if not, the default value
     */
    int getConfigAsInt(ConfigKey pKey);

    /**
     * Get processing parameters from a string-string map
     *
     * @param pParams params to extra. A parameter "p" is used as extra path info
     * @return the processing parameters
     */
    ProcessingParameters getProcessingParameters(Map<String, String> pParams);

    /**
     * Get an configuration value as boolean value. The value must
     * be configured as 'true' for this method to return true
     *
     * @param pKey the configuration key for which a boolean config value is requested
     * @return true if the configuration (or the default value, if the configuration is not set)
     *         is "true" for this key, false otherwise.
     */
    boolean getConfigAsBoolean(ConfigKey pKey);

}
