package org.jolokia.support.spring;

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
import org.jolokia.support.spring.backend.SpringRequestHandler;
import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.api.LogHandler;
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
    private SpringJolokiaConfigHolder configHolder;

    // Whether to lookup up other configurations in the context
    private boolean lookupConfig = false;

    // Whether to lookup Jolokia services from the spring context
    private boolean lookupServices = false;

    // Whether to expose the spring container itself via Jolokia
    private boolean exposeApplicationContext = false;

    // How to deal with system properties
    private SystemPropertyMode systemPropertyMode;

    // Remember the context for dynamic lookup of multiple configs
    private ApplicationContext context;

    // Log handler to use. If not given it is class looked up from the configuration
    private SpringJolokiaLogHandlerHolder logHandlerHolder;

    /**
     * Callback used for initializing and optionally starting up the server
     */
    public void afterPropertiesSet() throws IOException {
        Map<String, String> config = new HashMap<String, String>();
        if (systemPropertyMode == SystemPropertyMode.FALLBACK) {
            config.putAll(lookupSystemProperties());
        }
        if (configHolder.getConfig() != null) {
            config.putAll(configHolder.getConfig());
        }

        if (lookupConfig && context != null) {
            config.putAll(lookupConfigurationFromContext());
        }

        if (systemPropertyMode == SystemPropertyMode.OVERRIDE) {
            config.putAll(lookupSystemProperties());
        }

        // Spring specific config 'autoStart' gets removed herem
        boolean autoStart = Boolean.parseBoolean(config.remove("autoStart"));

        LogHandler logHandler = logHandlerHolder != null ? logHandlerHolder.getLogHandler() : null;
        init(new JolokiaServerConfig(config), logHandler);

        if (exposeApplicationContext && context != null) {
            addService(new SpringRequestHandler(context, 100));
        }

        if (lookupServices) {
            lookupServices();
        }

        if (autoStart) {
            start();
        }
    }

    private void lookupServices() {
        Map<String,JolokiaService> services = context.getBeansOfType(JolokiaService.class);
        for (JolokiaService service : services.values()) {
            addService(service);
        }
    }

    private Map<String,String> lookupConfigurationFromContext() {
        // Merge all configs in the context in the reverse order
        Map<String,String> config = new HashMap<String, String>();
        Map<String, SpringJolokiaConfigHolder> configsMap = context.getBeansOfType(SpringJolokiaConfigHolder.class);
        List<SpringJolokiaConfigHolder> configs = new ArrayList<SpringJolokiaConfigHolder>(configsMap.values());
        Collections.sort(configs, new OrderComparator());
        for (SpringJolokiaConfigHolder c : configs) {
            if (c != this.configHolder) {
                config.putAll(c.getConfig());
            }
        }
        return config;
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
        configHolder = pConfig;
    }

    /**
     * Set the log handler to use which is contained in the given holder
     *
     * @param pLogHandlerHolder holder of a log handler
     */
    public void setLogHandler(SpringJolokiaLogHandlerHolder pLogHandlerHolder) {
        logHandlerHolder = pLogHandlerHolder;
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
     * Whether to expose the spring container itself for outside access via an own Spring reaml '@spring'
     *
     * @param pExposeApplicationContext true if the container itself should be exposed via Jolokia. Default is false
     */
    public void setExposeApplicationContext(boolean pExposeApplicationContext) {
        exposeApplicationContext = pExposeApplicationContext;
    }

    /**
     * Whether to lookup {@link JolokiaService}s from the application context. These are
     * added according to their order to the set of the services present.
     *
     * @param pLookupServices whether to lookup jolokia services.
     */
    public void setLookupServices(boolean pLookupServices) {
        lookupServices = pLookupServices;
    }

    /**
     * Look for the appropriate configuration, merge multiple ones if given and start up
     * the Jolokia Server if lookupConfig is true
     *
     * @param pContext spring context containing the bean definition
     */
    public void setApplicationContext(ApplicationContext pContext)  {
        context = pContext;
    }

    /**
     * Set the system property mode for how to deal with configuration coming from system properties
     */
    public void setSystemPropertiesMode(String pMode) {
        systemPropertyMode = SystemPropertyMode.fromMode(pMode);
        if (systemPropertyMode == null) {
            systemPropertyMode = SystemPropertyMode.NEVER;
        }
    }

    /**
     * Set spring context id, required because an ID can be given. Not used.
     *
     * @param pId id to set
     */
    public void setId(String pId) {}
}
