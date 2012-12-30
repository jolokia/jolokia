/*
 * Copyright 2009-2012  Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.jvmagent.spring.config;

import org.jolokia.jvmagent.spring.SpringJolokiaServer;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Bean definition parser for a &lt;jolokia:server&gt; spring configuration
 *
 */
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
        return builder.getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
