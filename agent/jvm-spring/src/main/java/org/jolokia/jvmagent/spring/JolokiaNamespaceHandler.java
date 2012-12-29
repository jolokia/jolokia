package org.jolokia.jvmagent.spring;

import org.springframework.beans.factory.xml.*;

/**
 * @author roland
 * @since 29.12.12
 */
public class JolokiaNamespaceHandler extends NamespaceHandlerSupport {

    /** {@inheritDoc} */
    public void init() {
        registerBeanDefinitionParser("server",new ServerBeanDefinitionParser());
        registerBeanDefinitionParser("config",new ConfigBeanDefinitionParser());
    }

}
