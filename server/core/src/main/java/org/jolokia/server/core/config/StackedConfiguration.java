package org.jolokia.server.core.config;

import java.util.*;

/**
 * A configuration which delegates to other configuration services. The included
 * configuration will be asked one after each other and the first one holding a
 * config "wins". This aggregate configuration doesnt cache the value in order
 * to allow dynamic config changes.
 *
 * @author roland
 * @since 10.06.13
 */
public class StackedConfiguration implements Configuration {

    // List of delegated configuration objects
    private List<Configuration> delegateConfigs;

    /**
     * Generated this configuration stack from the given configurations
     *
     * @param configs configs to stack
     */
    public StackedConfiguration(Configuration ... configs) {
        delegateConfigs = Arrays.asList(configs);
    }

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pKey) {
        for (Configuration config : delegateConfigs) {
            if (config.containsKey(pKey)) {
                return config.getConfig(pKey);
            }
        }
        return pKey.getDefaultValue();
    }

    /** {@inheritDoc} */
    public Set<ConfigKey> getConfigKeys() {
        Set<ConfigKey> keys = new HashSet<ConfigKey>();
        for (Configuration config : delegateConfigs) {
            keys.addAll(config.getConfigKeys());
        }
        return keys;
    }

    /** {@inheritDoc} */
    public boolean containsKey(ConfigKey pKey) {
        for (Configuration config : delegateConfigs) {
            if (config.containsKey(pKey)) {
                return true;
            }
        }
        return false;
    }
}
