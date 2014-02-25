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

package org.jolokia.agent.core.config;

import java.util.*;

/**
 * A configuration coming from various sources.
 * Still a dummy for now
 *
 * @author roland
 * @since 22.04.13
 */
public class CascadingConfiguration implements Configuration {

    private SortedSet<Configuration> configurations;

    /**
     * Constructor
     *
     * @param pConfigurations configurations to merge
     */
    public CascadingConfiguration(SortedSet<Configuration> pConfigurations) {
        configurations = pConfigurations;
    }

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

    /** {@inheritDoc} */
    public boolean containsKey(ConfigKey pKey) {
        for (Configuration config : getConfigurations()) {
            if (config.containsKey(pKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the managed configurations
     *
     * @return configurations used
     */
    SortedSet<Configuration> getConfigurations() {
        return configurations;
    }
}
