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

import org.jolokia.jvmagent.spring.SpringJolokiaAgent;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Bean definition parser for a &lt;jolokia:agent&gt; spring configuration
 *
 */
public class AgentBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        Element config = DomUtils.getChildElementByTagName(element,"config");
        if (config != null) {
            builder.addPropertyValue("config", parserContext.getDelegate().parseCustomElement(config,builder.getRawBeanDefinition()));
        }
        String lookupConfig = element.getAttribute("lookupConfig");
        if (StringUtils.hasLength(lookupConfig)) {
            builder.addPropertyValue("lookupConfig", Boolean.parseBoolean(lookupConfig));
        } else {
            builder.addPropertyValue("lookupConfig",false);
        }
        String systemPropertiesMode = element.getAttribute("systemPropertiesMode");
        if (StringUtils.hasLength(systemPropertiesMode)) {
            builder.addPropertyValue("systemPropertiesMode",systemPropertiesMode);
        }
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        return SpringJolokiaAgent.class;
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
