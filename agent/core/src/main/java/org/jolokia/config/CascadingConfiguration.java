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

import java.util.*;

import org.jolokia.http.ProcessingParameters;

/**
 * @author roland
 * @since 22.04.13
 */
public class CascadingConfiguration implements Configuration {

    private SortedSet<Configuration> configurations;

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pKey) {
        for (Configuration config : getConfigurations()) {
            if (config.containsKey(pKey)) {
                return config.getConfig(pKey);
            }
        }
        return pKey.getDefaultValue();
    }

    /** {@inheritDoc} */
    public Set<ConfigKey> getConfigKeys() {
        Set<ConfigKey> ret = new HashSet<ConfigKey>();
        for (Configuration config : getConfigurations()) {
            ret.addAll(config.getConfigKeys());
        }
        return ret;
    }

    public ProcessingParameters getProcessingParameters(Map<String, String> pParams) {
        return null;
    }

    public boolean containsKey(ConfigKey pKey) {
        for (Configuration config : getConfigurations()) {
            if (config.containsKey(pKey)) {
                return true;
            }
        }
        return false;
    }

    SortedSet<Configuration> getConfigurations() {
        return configurations;
    }
}
