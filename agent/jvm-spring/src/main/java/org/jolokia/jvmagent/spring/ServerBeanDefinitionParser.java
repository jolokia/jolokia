package org.jolokia.jvmagent.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

public class ServerBeanDefinitionParser implements BeanDefinitionParser {

    public BeanDefinition parse(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SpringJolokiaServer.class);
        Element config = DomUtils.getChildElementByTagName(element,"config");
        if (config != null) {
            ConfigBeanDefinitionParser configParser = new ConfigBeanDefinitionParser();
            builder.addPropertyValue("config", configParser.parse(config, parserContext));
        }
        String lookupConfig = element.getAttribute("lookup-config");
        if (lookupConfig != null) {
            builder.addPropertyValue("lookupConfig", Boolean.parseBoolean(lookupConfig));
        }
        return builder.getBeanDefinition();
    }
}
