package org.jolokia.jvmagent.spring;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

public class ServerBeanDefinitionParser extends AbstractBeanDefinitionParser {

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SpringJolokiaServer.class);
        Element config = DomUtils.getChildElementByTagName(element,"config");
        if (config != null) {
            ConfigBeanDefinitionParser configParser = new ConfigBeanDefinitionParser();
            builder.addPropertyValue("config", configParser.parseInternal(config, parserContext));
        }
        String lookupConfig = element.getAttribute("lookupConfig");
        if (lookupConfig != null) {
            builder.addPropertyValue("lookupConfig", Boolean.parseBoolean(lookupConfig));
        }
        builder.addPropertyValue("id",element.getAttribute("id"));
        return builder.getBeanDefinition();
    }
}
