package org.jolokia.support.jmx;

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

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.server.core.service.impl.MBeanRegistry;
import org.testng.Assert;
import org.testng.annotations.*;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 13.01.13
 */
public class JolokiaMBeanServerUtilTest {

    MBeanRegistry handler;

    @BeforeClass
    public void setup() {
        handler = new MBeanRegistry();
    }

    @AfterClass
    public void tearDown() throws JMException {
        handler.destroy();
    }

    @Test
    public void checkNotRegistered() throws MalformedObjectNameException {
        MBeanServer jolokiaServer = JolokiaMBeanServerUtil.getJolokiaMBeanServer();
        Assert.assertNotEquals(ManagementFactory.getPlatformMBeanServer(), jolokiaServer);
        for (MBeanServer server : MBeanServerFactory.findMBeanServer(null)) {
            Assert.assertNotEquals(server, jolokiaServer);
        }
    }

    @Test
    public void registerMBean() throws MalformedObjectNameException, NotCompliantMBeanException,
                                       InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer jolokiaServer = JolokiaMBeanServerUtil.getJolokiaMBeanServer();

        Dummy test = new Dummy();
        ObjectName name = new ObjectName("jolokia.test:name=test");
        JolokiaMBeanServerUtil.registerMBean(test, name);
        assertTrue(jolokiaServer.isRegistered(name));
        JolokiaMBeanServerUtil.unregisterMBean(name);
        Assert.assertFalse(jolokiaServer.isRegistered(name));
    }


    interface DummyMBean {

    }
    private class Dummy implements DummyMBean {
    }
}
