package org.jolokia.server.core.request;

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

import java.util.HashMap;
import java.util.Map;

import org.jolokia.server.core.config.ConfigKey;

/**
 * Class encapsulating parameters used during processing
 *
 * @author roland
 * @since 07.02.13
 */
public class ProcessingParameters {

    // Request specific parameters
    private final Map<ConfigKey,String> params;

    /**
     * Constructor which is already filtered and splitted
     *
     * @param pConfig configuration to use
     */
    public ProcessingParameters(Map<ConfigKey, String> pConfig) {
        params = pConfig;
    }

    /**
     * Get a processing parameter
     *
     * @param pKey key to lookup
     * @return the value or the default value from the key if no config value is set
     */
    public String get(ConfigKey pKey) {
        String value = params.get(pKey);
        if (value != null) {
            return value;
        } else {
            return pKey.getDefaultValue();
        }
    }

    /**
     * Merge in a configuration and return a ProcessingParameters object representing
     * the merged values
     *
     * @param pConfig config to merge in
     * @return a new ProcessingParameters instance if the given config is not null. Otherwise this object
     *         is returned.
     */
    public ProcessingParameters mergedParams(Map<String, String> pConfig) {
        if (pConfig == null) {
            return this;
        } else {
            Map<ConfigKey,String> newParams = new HashMap<>();
            newParams.putAll(params);
            newParams.putAll(convertToConfigMap(pConfig));
            return new ProcessingParameters(newParams);
        }
    }

    /**
     * Get the path info represented with this processing parameters or null if no
     * path info is given
     *
     * @return pathinfo or null if no pathinfo is set
     */
    public String getPathInfo() {
        return params.get(ConfigKey.PATH_QUERY_PARAM);
    }

    // Convert a string-string map to one with ConfigKeys. All parameters which doesn't
    // map to a ConfigKey are filtered out
    private Map<ConfigKey, String> convertToConfigMap(Map<String, String> pParams) {
        Map<ConfigKey,String> config = new HashMap<>();
        if (pParams != null) {
            for (Map.Entry<String,?> entry : pParams.entrySet()) {
                ConfigKey cKey = ConfigKey.getRequestConfigKey(entry.getKey());
                if (cKey != null) {
                    Object value = entry.getValue();
                    config.put(cKey, value != null ? value.toString() : null);
                }
            }
        }
        return config;
    }
}
