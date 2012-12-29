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
    private SpringJolokiaConfig config;

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
        Map<String,String> finalConfig = new HashMap<String, String>();
        finalConfig.putAll(config.getConfig());
        if (lookupConfig) {
            // Merge all configs in the context in the reverse order
            Map<String, SpringJolokiaConfig> configsMap = context.getBeansOfType(SpringJolokiaConfig.class);
            List<SpringJolokiaConfig> configs = new ArrayList<SpringJolokiaConfig>(configsMap.values());
            Collections.sort(configs, new OrderComparator());
            for (SpringJolokiaConfig c : configs) {
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
        final Map<String, String> configMap = finalConfig;
        init(new ServerConfig(configMap),false);
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
    public void setConfig(SpringJolokiaConfig pConfig) {
        config = pConfig;
    }

    /**
     * Whether to lookup dynamically configs in the application context after creation
     * of this bean. This especially useful if the server is automatically started in a different
     * module and needs some extra customization
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
     * Set spring context id, required because an ID is required.
     *
     * @param pId id to set
     */
    public void setId(String pId) {
        id = pId;
    }

    // ===================================================================

    private static class ServerConfig extends JolokiaServerConfig {

        private ServerConfig(Map<String,String> config) {
            Map<String,String> finalCfg = getDefaultConfig();
            finalCfg.putAll(config);
            init(finalCfg);
        }
    }
}
