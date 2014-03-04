package org.jolokia.support.jmx.impl;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.service.serializer.JolokiaSerializer;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;

/**
 * @author roland
 * @since 04.03.14
 */
public class MBeanServerHolderTest {

     @Test
    void registerMBean2() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        MBeanServer ret = MBeanServerFactory.newMBeanServer();
        ObjectName oName = new ObjectName(JolokiaMBeanServerHolder.OBJECT_NAME);
        EasyMock.expect(server.registerMBean(anyObject(), eq(oName))).andThrow(new InstanceAlreadyExistsException());
        EasyMock.expect(server.getAttribute(eq(oName), eq(JolokiaMBeanServerHolder.JOLOKIA_MBEAN_SERVER_ATTRIBUTE))).andReturn(ret);
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server,new JolokiaSerializer());
        Assert.assertEquals(ret, m);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void registerMBeanFailed() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = new ObjectName(JolokiaMBeanServerHolder.OBJECT_NAME);
        EasyMock.expect(server.registerMBean(anyObject(), eq(oName))).andThrow(new MBeanRegistrationException(new Exception()));
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server,new JolokiaSerializer());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void registerMBeanFailed2() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = new ObjectName(JolokiaMBeanServerHolder.OBJECT_NAME);
        EasyMock.expect(server.registerMBean(anyObject(), eq(oName))).andThrow(new InstanceAlreadyExistsException());
        EasyMock.expect(server.getAttribute(eq(oName), eq(JolokiaMBeanServerHolder.JOLOKIA_MBEAN_SERVER_ATTRIBUTE))).andThrow(new AttributeNotFoundException());
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server,new JolokiaSerializer());
    }
}
