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
public class ConfigurationImpl implements Configuration {

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
    public ConfigurationImpl(Object... keyAndValues) {
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
            String value = pConfig.get(c.getKeyValue());
            if (value != null) {
                globalConfig.put(c,value);
            }
        }
    }

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pKey) {
            String value = globalConfig.get(pKey);
            if (value == null) {
                value = pKey.getDefaultValue();
            }
            return value;
    }

    /** {@inheritDoc} */
    public int getConfigAsInt(ConfigKey pKey) {
        int ret;
        try {
            ret = Integer.parseInt(getConfig(pKey));
        } catch (NumberFormatException exp) {
            ret = Integer.parseInt(pKey.getDefaultValue());
        }
        return ret;
    }

    /** {@inheritDoc} */
    public boolean getConfigAsBoolean(ConfigKey pKey) {
        return Boolean.valueOf(getConfig(pKey));
    }

    /** {@inheritDoc} */
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
     * Get the number of stored configuration values
     *
     * @return number of configuration values
     */
    public int size() {
        return globalConfig.size();
    }
}
