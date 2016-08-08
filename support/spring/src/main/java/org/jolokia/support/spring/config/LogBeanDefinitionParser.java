package org.jolokia.support.spring.config;

import org.jolokia.support.spring.SpringJolokiaLogHandlerHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * Handler for parsing "log" configurations
 *
 * @author roland
 * @since 17.10.13
 */
public class LogBeanDefinitionParser extends AbstractBeanDefinitionParser {

    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SpringJolokiaLogHandlerHolder.class);
        String logRef = element.getAttribute("log-ref");
        if (StringUtils.hasLength(logRef)) {
            builder.addPropertyReference("logHandler",logRef);
        }
        for (String attr : new String[]{ "type", "category"}) {
            String val = element.getAttribute(attr);
            if (StringUtils.hasLength(val)) {
                builder.addPropertyValue(attr,val);
            }
        }
        return builder.getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
