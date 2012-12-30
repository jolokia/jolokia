/*
 * Copyright 2009-2012  Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.jvmagent.spring;

import java.util.*;

import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.springframework.beans.BeansException;
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
public class SpringJolokiaServer extends JolokiaServer implements ApplicationContextAware, InitializingBean, DisposableBean {

    // Spring id
    private String id;

    // Default configuration to use
    private SpringJolokiaConfigWrapper config;

    // Whether to lookup up other configurations in the context
    private boolean lookupConfig = false;

    // Remember the context for dynamic lookup of multiple configs
    private ApplicationContext context;

    /**
     * Callback used for initializing and optionally starting up the server
     *
     * @throws Exception
     */
    public void afterPropertiesSet() throws Exception {
        Map<String, String> finalConfig = new HashMap<String, String>();
        finalConfig.putAll(config.getConfig());
        if (lookupConfig) {
            // Merge all configs in the context in the reverse order
            Map<String, SpringJolokiaConfigWrapper> configsMap = context.getBeansOfType(SpringJolokiaConfigWrapper.class);
            List<SpringJolokiaConfigWrapper> configs = new ArrayList<SpringJolokiaConfigWrapper>(configsMap.values());
            Collections.sort(configs, new OrderComparator());
            for (SpringJolokiaConfigWrapper c : configs) {
                if (c != config) {
                    finalConfig.putAll(c.getConfig());
                }
            }
        }
        String autoStartS = finalConfig.remove("autoStart");
        boolean autoStart = false;
        if (autoStartS != null) {
            autoStart = Boolean.parseBoolean(autoStartS);
        }
        init(new ServerConfig(finalConfig),false);
        if (autoStart) {
            start();
        }
    }

    /**
     * Stop the server
     *
     * @throws Exception
     */
    public void destroy() throws Exception {
        stop();
    }

    /**
     * Set the configuration which is used, if no other configuration options are given
     *
     * @param pConfig configuration to use
     */
    public void setConfig(SpringJolokiaConfigWrapper pConfig) {
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
     * @throws BeansException
     */
    public void setApplicationContext(ApplicationContext pContext) throws BeansException {
        if (lookupConfig) {
            context = pContext;
        }
    }

    /**
     * Set spring context id, required because an ID can be given. Not used otherwise.
     *
     * @param pId id to set
     */
    public void setId(String pId) {
        id = pId;
    }

    // ===================================================================

    // Simple extenstion to the JolokiaServerConfig in order to do the proper initialization
    private static class ServerConfig extends JolokiaServerConfig {

        private ServerConfig(Map<String,String> config) {
            Map<String,String> finalCfg = getDefaultConfig();
            finalCfg.putAll(config);
            init(finalCfg);
        }
    }
}
