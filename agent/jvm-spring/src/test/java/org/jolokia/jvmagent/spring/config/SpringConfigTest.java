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

import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.*;

import org.jolokia.jvmagent.spring.SpringJolokiaConfigHolder;
import org.jolokia.jvmagent.spring.SpringJolokiaServer;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 29.12.12
 */
public class SpringConfigTest {

    @Test
    public void nameSpaceHandler() {
        JolokiaNamespaceHandler handler = new JolokiaNamespaceHandler();
        handler.init();
    }

    @Test
    public void simpleServer() throws ParserConfigurationException, IOException, SAXException {
        Element element = getElement("/simple-server.xml");
        ServerBeanDefinitionParser parser = new ServerBeanDefinitionParser();
        assertTrue(parser.shouldGenerateIdAsFallback());
        BeanDefinition bd = parser.parseInternal(element, null);
        assertEquals(bd.getBeanClassName(), SpringJolokiaServer.class.getName());
        MutablePropertyValues props = bd.getPropertyValues();
        assertEquals(props.size(),2);
        assertEquals(props.getPropertyValue("lookupConfig").getValue(), false);
        BeanDefinition cBd = (BeanDefinition) props.getPropertyValue("config").getValue();;
        assertEquals(cBd.getBeanClassName(),SpringJolokiaConfigHolder.class.getName());
        MutablePropertyValues cProps = cBd.getPropertyValues();
        assertEquals(cProps.size(),1);
        verifyConfig(cProps);
    }

    @Test
    public void simpleConfig() throws Exception {
        Element element = getElement("/simple-config.xml");
        ConfigBeanDefinitionParser parser = new ConfigBeanDefinitionParser();
        BeanDefinition bd = parser.parseInternal(element, null);
        assertEquals(bd.getBeanClassName(),SpringJolokiaConfigHolder.class.getName());
        MutablePropertyValues cProps = bd.getPropertyValues();
        assertEquals(cProps.size(),2);
        verifyConfig(cProps);
        assertEquals(cProps.getPropertyValue("order").getValue(), 1000);
    }


    private Element getElement(String pXmlPath) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(getClass().getResourceAsStream(pXmlPath));
        return doc.getDocumentElement();
    }

    private void verifyConfig(MutablePropertyValues pCProps) {
        Map vals = (Map) pCProps.getPropertyValue("config").getValue();
        assertEquals(vals.size(),3);
        for (String k : new String[] { "host", "port", "autoStart" }) {
            assertTrue(vals.containsKey(k));
        }
    }


}
