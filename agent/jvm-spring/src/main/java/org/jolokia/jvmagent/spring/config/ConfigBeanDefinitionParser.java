package org.jolokia.jvmagent.spring.config;

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

import java.util.*;

import org.jolokia.jvmagent.spring.SpringJolokiaConfigHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

/**
 * Definition parser for "config"
 *
* @author roland
* @since 29.12.12
*/
class ConfigBeanDefinitionParser extends AbstractBeanDefinitionParser {

    // Properties to ignore for setting the configuration
    private static final String SKIP_ATTRIBUTES[] = {
            "order",
            "xmlns"
    };

    private Set<String>         skipMap;

    ConfigBeanDefinitionParser() {
        skipMap = new HashSet<String>(Arrays.asList(SKIP_ATTRIBUTES));
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SpringJolokiaConfigHolder.class);
        Map<String, Object> config = new HashMap<String, Object>();
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0;i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            if (skipMap.contains(name)) {
                continue;
            }
            config.put(name, attr.getValue());
        }
        builder.addPropertyValue("config",config);
        String order = element.getAttribute("order");
        if (StringUtils.hasText(order)) {
            builder.addPropertyValue("order",Integer.parseInt(order));
        }
        return builder.getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
