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
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

/**
 * Definition parser for "config"
 *
* @author roland
* @since 29.12.12
*/
class ConfigBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    // Properties to ignore for setting the configuration
    private static final String SKIP_ATTRIBUTES[] = {
            "order",
            "xmlns",
            "xmlns:xsi",
            "xsi:schemaLocation",
            "id"
    };

    private Set<String>         skipMap;

    ConfigBeanDefinitionParser() {
        skipMap = new HashSet<String>(Arrays.asList(SKIP_ATTRIBUTES));
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        Map<String, Object> config = new HashMap<String, Object>();
        builder.addPropertyValue("config",createConfigMap(element.getAttributes()));
        String order = element.getAttribute("order");
        if (StringUtils.hasText(order)) {
            builder.addPropertyValue("order",Integer.parseInt(order));
        }
    }

    private Map<Object, Object> createConfigMap(NamedNodeMap attributes) {
        ManagedMap<Object, Object> map = new ManagedMap<Object, Object>(attributes.getLength());
		map.setKeyTypeName("java.lang.String");
		map.setValueTypeName("java.lang.String");

        for (int i = 0;i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getName();
            if (skipMap.contains(name)) {
                continue;
            }
            Object key = new TypedStringValue(name, String.class);
            Object value = new TypedStringValue(attr.getValue(),String.class);
            map.put(key,value);
        }
        return map;
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        return SpringJolokiaConfigHolder.class;
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
