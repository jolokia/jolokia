package org.jolokia.util;

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 07.03.13
 */
public class JmxUtilTest implements NotificationListener {


    private int counter = 0;

    @Test
    public void newObjectName() throws MalformedObjectNameException {
        ObjectName name = JmxUtil.newObjectName("java.lang:type=blub");
        assertEquals(name, new ObjectName("java.lang:type=blub"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*Invalid.*sogehtsnicht.*")
    public void invalidObjectName() {
        JmxUtil.newObjectName("bla:blub:name=sogehtsnicht");
    }

    @Test
    public void addNotificationListenerTest() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        counter = 0;
        JmxUtil.addMBeanRegistrationListener(server,this,null);
        ObjectName name = JmxUtil.newObjectName("test:name=demo");
        server.registerMBean(new Bla(),name);
        assertEquals(counter, 1);
        server.unregisterMBean(name);
        assertEquals(counter, 2);
        JmxUtil.removeMBeanRegistrationListener(server,this);
        server.registerMBean(new Bla(),name);
        assertEquals(counter, 2);
        server.unregisterMBean(name);
        assertEquals(counter, 2);
        counter = 0;
    }

    @Test
    public void addNotificationListenerTestWithFilter() throws MBeanRegistrationException, InstanceNotFoundException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name1 = JmxUtil.newObjectName("test:name=demo");
        ObjectName nameFilter = JmxUtil.newObjectName("test:name=registered");

        JmxUtil.addMBeanRegistrationListener(server,this,nameFilter);

        counter = 0;

        server.registerMBean(new Bla(),name1);
        assertEquals(counter, 0);
        server.unregisterMBean(name1);
        assertEquals(counter, 0);
        server.registerMBean(new Bla(),nameFilter);
        assertEquals(counter, 1);
        server.unregisterMBean(nameFilter);
        assertEquals(counter, 2);

        JmxUtil.removeMBeanRegistrationListener(server,this);

        server.registerMBean(new Bla(),nameFilter);
        assertEquals(counter, 2);
        server.unregisterMBean(nameFilter);
        assertEquals(counter, 2);

        counter = 0;
    }

    @Test
    public void unknowListenerDeregistrationShouldBeSilentlyIgnored() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxUtil.removeMBeanRegistrationListener(server,this);

    }

    public void handleNotification(Notification notification, Object handback) {
        counter++;
    }

    public static class Bla implements BlaMBean {}
    public static interface BlaMBean {}
}
