package org.jolokia.config;

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

import java.util.*;

/**
 * Class encapsulating all Agent configuration, global config and processing parameters
 *
 * @author roland
 * @since 07.02.13
 */
public class Configuration {

    // Alternative query parameter for providing path info
    public static final String PATH_QUERY_PARAM = "p";

    // The global configuration given during startup
    private Map<ConfigKey, String> globalConfig;

    /**
     * Convenience constructor for setting up base configuration with key values pairs. This constructor
     * is especially suited for unit tests.
     *
     * @param keyAndValues an array with even number of elements and ConfigKey and String alternating
     */
    public Configuration(Object ... keyAndValues) {
        globalConfig = new HashMap<ConfigKey, String>();
        for (int i = 0;i < keyAndValues.length; i+= 2) {
            globalConfig.put( (ConfigKey) keyAndValues[i], (String) keyAndValues[i+1]);
        }
    }

    /**
     * Update the configuration hold by this object
     *
     * @param pExtractor an extractor for retrieving the configuration from some external object
     */
    public void updateGlobalConfiguration(ConfigExtractor pExtractor) {
        Enumeration e = pExtractor.getNames();
        while (e.hasMoreElements()) {
            String keyS = (String) e.nextElement();
            ConfigKey key = ConfigKey.getGlobalConfigKey(keyS);
            if (key != null) {
                globalConfig.put(key,pExtractor.getParameter(keyS));
            }
        }

    }

    /**
     * Update this global configuration from a string-string. Only the known keys are taken
     * from this map
     *
     * @param pConfig config map from where to take the configuration
     */
    public void updateGlobalConfiguration(Map<String, String> pConfig) {
        for (ConfigKey c : ConfigKey.values()) {
            if (c.isGlobalConfig()) {
                String value = pConfig.get(c.getKeyValue());
                if (value != null) {
                    globalConfig.put(c,value);
                }
            }
        }
    }

    /**
     * Get a configuration value if set as configuration or the default
     * value if not
     *
     * @param pKey the configuration key to lookup
     * @return the configuration value or the default value if no configuration
     *         was given.
     */
    public String get(ConfigKey pKey) {
        String value = globalConfig.get(pKey);
        if (value == null) {
            value = pKey.getDefaultValue();
        }
        return value;
    }

    /**
     * Get an configuration value as int value
     * @param pKey the configuration key
     * @return the value set or, if not, the default value
     */
    public int getAsInt(ConfigKey pKey) {
        int ret;
        try {
            ret = Integer.parseInt(get(pKey));
        } catch (NumberFormatException exp) {
            ret = Integer.parseInt(pKey.getDefaultValue());
        }
        return ret;
    }

    /**
     * Get processing parameters from a string-string map
     *
     * @param pParams params to extra. A parameter "p" is used as extra path info
     * @return the processing parameters
     */
    public ProcessingParameters getProcessingParameters(Map<String,String> pParams) {
        Map<ConfigKey,String> procParams = ProcessingParameters.convertToConfigMap(pParams);
        for (Map.Entry<ConfigKey,String> entry : globalConfig.entrySet()) {
            ConfigKey key = entry.getKey();
            if (key.isRequestConfig() && !procParams.containsKey(key)) {
                procParams.put(key,entry.getValue());
            }
        }
        return new ProcessingParameters(procParams,pParams.get(PATH_QUERY_PARAM));
    }

    /**
     * Get an configuration value as boolean value. The value must
     * be configured as 'true' for this method to return true
     *
     * @param pKey the configuration key for which a boolean config value is requested
     * @return true if the configuration (or the default value, if the configuration is not set)
     *         is "true" for this key, false otherwise.
     */
    public boolean getAsBoolean(ConfigKey pKey) {
        return Boolean.valueOf(get(pKey));
    }

    /**
     * Get the number of stored configuration values
     *
     * @return number of configuration values
     */
    public int size() {
        return globalConfig.size();
    }

    /**
     * Check whether a key is explicitely provided
     * @param pKey key to check
     * @return true if the key is contained in the configuration, false otherwise
     */
    public boolean containsKey(ConfigKey pKey) {
        return globalConfig.containsKey(pKey);
    }
}
