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

package org.jolokia.core.config;

import java.util.*;

/**
 * Class encapsulating all Agent configuration, global config and processing parameters
 *
 * @author roland
 * @since 07.02.13
 */
public class StaticConfiguration implements Configuration {

    // The global configuration given during startup
    private Map<ConfigKey, String> configMap;

    /**
     * Convenience constructor for setting up base configuration with key values pairs. This constructor
     * is especially suited for unit tests.
     *
     * @param keyAndValues an array with even number of elements and ConfigKey and String alternating.
     */
    public StaticConfiguration(Object... keyAndValues) {
        int idx = 0;
        configMap = new HashMap<ConfigKey, String>();
        for (int i = idx;i < keyAndValues.length; i+= 2) {
            configMap.put((ConfigKey) keyAndValues[i], (String) keyAndValues[i + 1]);
        }
    }

    /**
     * Initialise this configuration from a string-string map. Only the known keys are taken
     * from the given map
     *
     * @param pConfig config map from where to take the configuration
     */
    public StaticConfiguration(Map<String,String> pConfig) {
        configMap = new HashMap<ConfigKey, String>();
        for (ConfigKey c : ConfigKey.values()) {
            String value = pConfig.get(c.getKeyValue());
            if (value != null) {
                configMap.put(c, value);
            }
        }
    }

    /**
     * Update the configuration hold by this object
     *
     * @param pExtractor an extractor for retrieving the configuration from some external object
     */
    public void update(ConfigExtractor pExtractor) {
        Enumeration e = pExtractor.getNames();
        while (e.hasMoreElements()) {
            String keyS = (String) e.nextElement();
            ConfigKey key = ConfigKey.getGlobalConfigKey(keyS);
            if (key != null) {
                configMap.put(key, pExtractor.getParameter(keyS));
            }
        }
    }

    /**
     * Update from another configuration object whose values take precedence
     *
     * @param pConfig update configuration from the given config
     */
    public void update(Configuration pConfig) {
        for (ConfigKey key : pConfig.getConfigKeys()) {
            configMap.put(key,pConfig.getConfig(key));
        }
    }

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pKey) {
        String value = configMap.get(pKey);
        if (value == null) {
            value = pKey.getDefaultValue();
        }
        return value;
    }

    /** {@inheritDoc} */
    public Set<ConfigKey> getConfigKeys() {
        return configMap.keySet();
    }

    /** {@inheritDoc} */
    public boolean containsKey(ConfigKey pKey) {
        return configMap.containsKey(pKey);
    }
}
