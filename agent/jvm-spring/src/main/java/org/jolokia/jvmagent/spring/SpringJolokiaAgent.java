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

import java.io.IOException;
import java.util.*;

import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.OrderComparator;

/**
 * A specialized JVM Agent for Spring environments
 *
 * @author roland
 * @since 26.12.12
 */
public class SpringJolokiaAgent extends JolokiaServer implements ApplicationContextAware, InitializingBean, DisposableBean {

    // Default configuration to use
    private SpringJolokiaConfigHolder config;

    // Whether to lookup up other configurations in the context
    private boolean lookupConfig = false;

    // How to deal with system properties
    private SystemPropertyMode systemPropertyMode;

    // Remember the context for dynamic lookup of multiple configs
    private ApplicationContext context;

    /**
     * Callback used for initializing and optionally starting up the server
     */
    public void afterPropertiesSet() throws IOException {
        Map<String, String> finalConfig = new HashMap<String, String>();
        if (systemPropertyMode == SystemPropertyMode.MODE_FALLBACK) {
            finalConfig.putAll(lookupSystemProperties());
        }
        if (config != null) {
            finalConfig.putAll(config.getConfig());
        }
        if (lookupConfig) {
            // Merge all configs in the context in the reverse order
            Map<String, SpringJolokiaConfigHolder> configsMap = context.getBeansOfType(SpringJolokiaConfigHolder.class);
            List<SpringJolokiaConfigHolder> configs = new ArrayList<SpringJolokiaConfigHolder>(configsMap.values());
            Collections.sort(configs, new OrderComparator());
            for (SpringJolokiaConfigHolder c : configs) {
                if (c != config) {
                    finalConfig.putAll(c.getConfig());
                }
            }
        }
        if (systemPropertyMode == SystemPropertyMode.MODE_OVERRIDE) {
            finalConfig.putAll(lookupSystemProperties());
        }
        String autoStartS = finalConfig.remove("autoStart");
        boolean autoStart = true;
        if (autoStartS != null) {
            autoStart = Boolean.parseBoolean(autoStartS);
        }
        init(new JolokiaServerConfig(finalConfig),false);
        if (autoStart) {
            start();
        }
    }

    // Lookup system properties for all configurations possible
    private Map<String, String> lookupSystemProperties() {
        Map<String,String> ret = new HashMap<String, String>();
        Enumeration propEnum = System.getProperties().propertyNames();
        while (propEnum.hasMoreElements()) {
            String prop = (String) propEnum.nextElement();
            if (prop.startsWith("jolokia.")) {
                String key = prop.substring("jolokia.".length());
                ret.put(key,System.getProperty(prop));
            }
        }
        return ret;
    }

    /**
     * Stop the server
     */
    public void destroy() {
        stop();
    }

    /**
     * Set the configuration which is used, if no other configuration options are given
     *
     * @param pConfig configuration to use
     */
    public void setConfig(SpringJolokiaConfigHolder pConfig) {
        config = pConfig;
    }

    /**
     * Whether to lookup dynamically configs in the application context after creation
     * of this bean. This especially useful if the server is automatically started in a different
     * module and needs some extra customization. Used e.g for the spring plugin.
     *
     * @param pLookupConfig whether to lookup configuration dynamically. Default is false.
     */
    public void setLookupConfig(boolean pLookupConfig) {
        lookupConfig = pLookupConfig;
    }

    /**
     * Look for the appropriate configuration, merge multiple ones if given and start up
     * the Jolokia Server if lookupConfig is true
     *
     * @param pContext spring context containing the bean definition
     */
    public void setApplicationContext(ApplicationContext pContext)  {
        if (lookupConfig) {
            context = pContext;
        }
    }

    /**
     * Set the system propert mode for how to deal with configuration coming from system properties
     *
     */
    public void setSystemPropertiesMode(String pMode) {
        systemPropertyMode = SystemPropertyMode.fromMode(pMode);
        if (systemPropertyMode == null) {
            systemPropertyMode = SystemPropertyMode.MODE_NEVER;
        }
    }

    /**
     * Set spring context id, required because an ID can be given. Not used.
     *
     * @param pId id to set
     */
    public void setId(String pId) {
    }

    // ===================================================================

}
