package org.jolokia.jvmagent.spring;

import java.util.HashMap;
import java.util.Map;

import org.jolokia.jvmagent.JolokiaServerConfig;
import org.springframework.core.Ordered;

/**
 * Configuration wrapper for a spring based configuration via map values. This bean
 * is order with lower order having higher precedence. The content of this object is
 * used for building up a {@link JolokiaServerConfig} for the server to start.
 *
 * @author roland
 * @since 28.12.12
 */
public class SpringJolokiaConfig extends JolokiaServerConfig implements Ordered {

    private Map<String, String> config = new HashMap<String, String>();

    private int order;

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> pConfig) {
        config = pConfig;
    }

    public void setOrder(int pOrder) {
        order = pOrder;
    }

    public int getOrder() {
        return order;
    }
}
