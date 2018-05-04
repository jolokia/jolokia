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

import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.jolokia.jvmagent.spring.SpringJolokiaAgent;
import org.jolokia.jvmagent.spring.SpringJolokiaConfigHolder;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
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
        assertEquals(props.size(),2);
        assertEquals(props.getPropertyValue("lookupConfig").getValue(), false);
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


    private void verifyConfig(MutablePropertyValues pCProps) {
        Map vals = (Map) pCProps.getPropertyValue("config").getValue();
        assertEquals(vals.size(),3);
        for (String k : new String[] { "host", "port", "autoStart" }) {
            assertTrue(vals.containsKey(new TypedStringValue(k,String.class)));
        }
    }


}
