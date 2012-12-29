package org.jolokia.jvmagent.spring;

import java.util.*;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

/**
* @author roland
* @since 29.12.12
*/
class ConfigBeanDefinitionParser implements BeanDefinitionParser {

    final static String SKIP_ATTRIBUTES[] = {
            "order",
            "xmlns"
    };

    private Set<String>         skipMap;

    ConfigBeanDefinitionParser() {
        skipMap = new HashSet<String>(Arrays.asList(SKIP_ATTRIBUTES));
    }

    public BeanDefinition parse(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SpringJolokiaConfig.class);
        Map<String, String> config = new HashMap<String, String>();
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0;i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            if (skipMap.contains(name)) {
                continue;
            }
            config.put(name,attr.getValue());
        }
        builder.addPropertyValue("config",config);
        String order = element.getAttribute("order");
        if (StringUtils.hasText(order)) {
            builder.addPropertyValue("order",Integer.parseInt(order));
        }
        return builder.getBeanDefinition();
    }
}
