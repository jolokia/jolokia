package org.jolokia.jvmagent.spring;

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

import javax.management.MBeanServer;

import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 29.12.12
 */
public class JolokiaServerIntegrationTest extends BaseServerTest {

    @Test
    public void simple() throws Exception {
        System.setProperty("jolokia.port", "" + EnvTestUtil.getFreePort());
        System.out.println("Port selected: " + System.getProperty("jolokia.port"));
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/spring-jolokia-context.xml");
        SpringJolokiaAgent server = (SpringJolokiaAgent) ctx.getBean("jolokia");
        JolokiaServerConfig cfg = server.getServerConfig();
        assertEquals(cfg.getContextPath(),"/j4p/");

        MBeanServer mbeanServer = (MBeanServer) ctx.getBean("jolokiaMBeanServer");
        assertNotNull(mbeanServer);
        checkServerAndStop(server);
    }

    @Test
    public void withPlainBean() throws Exception {
        System.setProperty("jolokia.port", "" + EnvTestUtil.getFreePort());
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/spring-jolokia-plain-beans.xml");
        SpringJolokiaAgent server = (SpringJolokiaAgent) ctx.getBean("jolokia");
        JolokiaServerConfig cfg = server.getServerConfig();
        assertEquals(cfg.getContextPath(),"/jolokia/");
        checkServerAndStop(server);
    }
}
