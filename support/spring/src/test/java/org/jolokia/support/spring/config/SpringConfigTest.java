package org.jolokia.support.spring.config;

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

import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.*;

import org.jolokia.support.spring.SpringJolokiaAgent;
import org.jolokia.support.spring.SpringJolokiaConfigHolder;
import org.jolokia.support.spring.SpringJolokiaLogHandlerHolder;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.BeforeTest;
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


    private DefaultListableBeanFactory beanFactory;
    private XmlBeanDefinitionReader reader;


    @BeforeTest
	public void setUp() {
		this.beanFactory = new DefaultListableBeanFactory();
        this.reader = new XmlBeanDefinitionReader(this.beanFactory);
	}

    @Test
    public void nameSpaceHandler() {
        JolokiaNamespaceHandler handler = new JolokiaNamespaceHandler();
        handler.init();
    }

    @Test
    public void simpleServer() throws ParserConfigurationException, IOException, SAXException {

        reader.loadBeanDefinitions(new ClassPathResource("/simple-server.xml"));

        BeanDefinition bd = beanFactory.getBeanDefinition("jolokiaServer");
        assertEquals(bd.getBeanClassName(), SpringJolokiaAgent.class.getName());
        MutablePropertyValues props = bd.getPropertyValues();
        assertEquals(props.size(),3);
        assertEquals(props.getPropertyValue("lookupConfig").getValue(), false);
        assertEquals(props.getPropertyValue("lookupServices").getValue(), false);
        BeanDefinition cBd = (BeanDefinition) props.getPropertyValue("config").getValue();;
        assertEquals(cBd.getBeanClassName(),SpringJolokiaConfigHolder.class.getName());
        MutablePropertyValues cProps = cBd.getPropertyValues();
        assertEquals(cProps.size(), 1);
        verifyConfig(cProps);
    }

    @Test
    public void simpleConfig() throws Exception {
        reader.loadBeanDefinitions(new ClassPathResource("/simple-config.xml"));

        BeanDefinition bd = beanFactory.getBeanDefinition("config");
        assertEquals(bd.getBeanClassName(),SpringJolokiaConfigHolder.class.getName());
        MutablePropertyValues cProps = bd.getPropertyValues();
        assertEquals(cProps.size(),2);
        verifyConfig(cProps);
        assertEquals(cProps.getPropertyValue("order").getValue(), 1000);
    }

    @Test
    public void logHandlerRef() throws IOException, SAXException, ParserConfigurationException {
        Element element = getElement("/simple-log-ref.xml");
        LogBeanDefinitionParser parser = new LogBeanDefinitionParser();
        BeanDefinition bd = parser.parseInternal(element,null);
        assertEquals(bd.getBeanClassName(), SpringJolokiaLogHandlerHolder.class.getName());
        MutablePropertyValues props = bd.getPropertyValues();
        assertEquals(props.size(),1);
        assertTrue(props.getPropertyValue("logHandler").getValue() instanceof BeanReference);
        assertEquals(((BeanReference) props.getPropertyValue("logHandler").getValue()).getBeanName(), "logHandler");
    }

    @Test
    public void logHandlerType() throws IOException, SAXException, ParserConfigurationException {
        Element element = getElement("/simple-log-type.xml");
        LogBeanDefinitionParser parser = new LogBeanDefinitionParser();
        BeanDefinition bd = parser.parseInternal(element, null);
        assertEquals(bd.getBeanClassName(), SpringJolokiaLogHandlerHolder.class.getName());
        MutablePropertyValues props = bd.getPropertyValues();
        assertEquals(props.size(),2);
        assertEquals(props.getPropertyValue("type").getValue(), "commons");
        assertEquals(props.getPropertyValue("category").getValue(),"bla");
    }

    private void verifyConfig(MutablePropertyValues pCProps) {
        Map vals = (Map) pCProps.getPropertyValue("config").getValue();
        assertEquals(vals.size(),3);
        for (String k : new String[] { "host", "port", "autoStart" }) {
            assertTrue(vals.containsKey(new TypedStringValue(k,String.class)));
        }
    }

    private Element getElement(String pXmlPath) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(getClass().getResourceAsStream(pXmlPath));
        return doc.getDocumentElement();
    }

}
