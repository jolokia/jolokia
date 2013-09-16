package org.jolokia.jmx;

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

import org.easymock.EasyMock;
import org.jolokia.backend.MBeanRegistry;
import org.testng.Assert;
import org.testng.annotations.*;

import static org.easymock.EasyMock.eq;
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

    @Test
    void registerMBean2() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        MBeanServer ret = MBeanServerFactory.newMBeanServer();
        ObjectName oName = new ObjectName(JolokiaMBeanServerHolderMBean.OBJECT_NAME);
        EasyMock.expect(server.registerMBean(EasyMock.anyObject(), EasyMock.eq(oName))).andThrow(new InstanceAlreadyExistsException());
        EasyMock.expect(server.getAttribute(EasyMock.eq(oName), eq(JolokiaMBeanServerUtil.JOLOKIA_MBEAN_SERVER_ATTRIBUTE))).andReturn(ret);
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerUtil.registerJolokiaMBeanServerHolderMBean(server);
        Assert.assertEquals(ret, m);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void registerMBeanFailed() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = new ObjectName(JolokiaMBeanServerHolderMBean.OBJECT_NAME);
        EasyMock.expect(server.registerMBean(EasyMock.anyObject(), EasyMock.eq(oName))).andThrow(new MBeanRegistrationException(new Exception()));
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerUtil.registerJolokiaMBeanServerHolderMBean(server);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void registerMBeanFailed2() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = new ObjectName(JolokiaMBeanServerHolderMBean.OBJECT_NAME);
        EasyMock.expect(server.registerMBean(EasyMock.anyObject(), EasyMock.eq(oName))).andThrow(new InstanceAlreadyExistsException());
        EasyMock.expect(server.getAttribute(EasyMock.eq(oName), eq(JolokiaMBeanServerUtil.JOLOKIA_MBEAN_SERVER_ATTRIBUTE))).andThrow(new AttributeNotFoundException());
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerUtil.registerJolokiaMBeanServerHolderMBean(server);
    }

    interface DummyMBean {

    }
    private class Dummy implements DummyMBean {
    }
}
