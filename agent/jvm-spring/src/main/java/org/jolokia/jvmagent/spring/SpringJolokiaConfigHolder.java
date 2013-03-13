package org.jolokia.jvmagent.spring;

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

import org.jolokia.jvmagent.JolokiaServerConfig;
import org.springframework.core.Ordered;

/**
 * Configuration wrapper for a spring based configuration. It simply wraps a string-string map
 * for values.
 * The content of this object is used for building up a {@link JolokiaServerConfig} for
 * the server to start.
 * <p>
 * Multiple config objects can be present in a context, their precedence in decided
 * upon the order: The higher the order, the more important the configuration is (overriding
 * lower ordered configs). A server will pick them up, if its <code>lookupConfigs</code> property
 * is set to true (by default it is "false").
 * <p>
 * You should use &lt;jolokia:config&gt; for defining configuration, either as standalone
 * configuration (if using the "plugin") or as an embedded element to &lt;jolokia:server&gt;
 *
 * @author roland
 * @since 28.12.12
 */
public class SpringJolokiaConfigHolder implements Ordered {

    // configuration
    private Map<String, String> config = new HashMap<String, String>();

    // order for multiple configurations available
    private int order;

    /**
     * Get tge configuration as a free-form map
     * @return config map
     */
    public Map<String, String> getConfig() {
        return config;
    }

    /**
     * Set the configuration values
     *
     * @param pConfig configuration to set
     */
    public void setConfig(Map<String, String> pConfig) {
        config = pConfig;
    }

    /**
     * Set the order or priority of this configuration. Higher orders mean higher priority.
     *
     * @param pOrder order to set
     */
    public void setOrder(int pOrder) {
        order = pOrder;
    }

    /** {@inheritDoc} */
    public int getOrder() {
        return order;
    }
}
