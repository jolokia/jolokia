package org.jolokia.support.jmx;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.server.core.util.jmx.MBeanServers;
import org.jolokia.service.serializer.JolokiaSerializer;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;

/**
 * @author roland
 * @since 04.03.14
 */
public class JolokiaMBeanServerHolderTest {

    @Test
    public void registerMBean2() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        MBeanServer ret = MBeanServerFactory.newMBeanServer();
        ObjectName oName = new ObjectName(MBeanServers.JOLOKIA_MBEAN_SERVER_NAME);
        EasyMock.expect(server.registerMBean(anyObject(), eq(oName))).andThrow(new InstanceAlreadyExistsException());
        EasyMock.expect(server.getAttribute(eq(oName), eq(JolokiaMBeanServerHolderMBean.JOLOKIA_MBEAN_SERVER_ATTRIBUTE))).andReturn(ret);
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server, new JolokiaSerializer());
        Assert.assertEquals(ret, m);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void registerMBeanFailed() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = new ObjectName(MBeanServers.JOLOKIA_MBEAN_SERVER_NAME);
        EasyMock.expect(server.registerMBean(anyObject(), eq(oName))).andThrow(new MBeanRegistrationException(new Exception()));
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server,new JolokiaSerializer());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void registerMBeanFailed2() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = new ObjectName(MBeanServers.JOLOKIA_MBEAN_SERVER_NAME);
        EasyMock.expect(server.registerMBean(anyObject(), eq(oName))).andThrow(new InstanceAlreadyExistsException());
        EasyMock.expect(server.getAttribute(eq(oName), eq(JolokiaMBeanServerHolderMBean.JOLOKIA_MBEAN_SERVER_ATTRIBUTE))).andThrow(new AttributeNotFoundException());
        EasyMock.replay(server);

        MBeanServer m = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server,new JolokiaSerializer());
    }

    @Test
    public void unregisterMBean() throws MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = JolokiaMBeanServerHolder.MBEAN_SERVER_HOLDER_OBJECTNAME;
        server.unregisterMBean(eq(oName));
        replay(server);

        JolokiaMBeanServerHolder.unregisterJolokiaMBeanServerHolderMBean(server);
        verify(server);
    }


    @Test
    public void unregisterMBean2() throws MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = JolokiaMBeanServerHolder.MBEAN_SERVER_HOLDER_OBJECTNAME;
        server.unregisterMBean(eq(oName));
        expectLastCall().andThrow(new InstanceNotFoundException());
        replay(server);

        JolokiaMBeanServerHolder.unregisterJolokiaMBeanServerHolderMBean(server);
        verify(server);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void unregisterMBeanFailed() throws MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer server = EasyMock.createMock(MBeanServer.class);
        ObjectName oName = JolokiaMBeanServerHolder.MBEAN_SERVER_HOLDER_OBJECTNAME;
        server.unregisterMBean(eq(oName));
        expectLastCall().andThrow(new MBeanRegistrationException(new RuntimeException()));
        replay(server);

        JolokiaMBeanServerHolder.unregisterJolokiaMBeanServerHolderMBean(server);
        verify(server);
    }
}
