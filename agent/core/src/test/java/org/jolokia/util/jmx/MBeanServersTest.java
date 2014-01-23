package org.jolokia.util.jmx;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;

import javax.management.*;

import org.jolokia.util.jmx.MBeanServers;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author roland
 * @since 07.03.13
 */
public class MBeanServersTest implements NotificationListener {

    MBeanServer ownServer = MBeanServerFactory.newMBeanServer();

    TestLookup lookup = new TestLookup();

    private boolean notificationCalled = false;

    @BeforeMethod
    public void setup() {
        notificationCalled = false;
    }

    @Test
    public void simple() throws ListenerNotFoundException, InstanceNotFoundException {
        MBeanServers servers = new MBeanServers(Collections.<MBeanServerConnection>singleton(ownServer), this);
        checkForServers(servers.getMBeanServers(), ManagementFactory.getPlatformMBeanServer(), ownServer);
        Assert.assertFalse(notificationCalled);

        servers.destroy();
        try {
            ManagementFactory.getPlatformMBeanServer().removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME,servers);
            Assert.fail("Exception should be thrown");
        } catch (ListenerNotFoundException exp) {

        }
    }


    @Test
    public void withJolokiaMBeanServerFromStart() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        registerJolokiaMBeanServer();
        MBeanServers servers = new MBeanServers(Collections.<MBeanServerConnection>singleton(ownServer), this);
        checkForServers(servers.getMBeanServers(),ManagementFactory.getPlatformMBeanServer(), ownServer,lookup.getJolokiaMBeanServer());
        Assert.assertFalse(notificationCalled);
        Assert.assertEquals(lookup.getJolokiaMBeanServer(), servers.getJolokiaMBeanServer());
        unregisterJolokiaMBeanServer();
        servers.destroy();

    }

    @Test
    public void withJolokiaMBeanServerFromKickingInLater() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        MBeanServers servers = new MBeanServers(Collections.<MBeanServerConnection>singleton(ownServer), this);
        registerJolokiaMBeanServer();

        MBeanServer jolokiaMBeanServer = lookup.getJolokiaMBeanServer();

        checkForServers(servers.getMBeanServers(),ManagementFactory.getPlatformMBeanServer(), ownServer,jolokiaMBeanServer);
        Assert.assertFalse(notificationCalled);
        jolokiaMBeanServer.registerMBean(new Dummy(), new ObjectName("dummy:type=dummy"));
        Assert.assertTrue(notificationCalled);
        Assert.assertEquals(jolokiaMBeanServer, servers.getJolokiaMBeanServer());
        jolokiaMBeanServer.unregisterMBean(new ObjectName("dummy:type=dummy"));
        unregisterJolokiaMBeanServer();
        servers.destroy();
    }

    @Test
    public void dump() {
        MBeanServers servers = new MBeanServers(Collections.<MBeanServerConnection>singleton(ownServer), this);
        String dump = servers.dump();
        Assert.assertTrue(dump.contains("java.lang"));
        Assert.assertTrue(dump.contains("type=Memory"));
    }

    private void checkForServers(Set<MBeanServerConnection> pFoundServers, MBeanServer... pToCheck) {
        int count = 0;
        for (MBeanServerConnection c : pFoundServers) {
            for (MBeanServer s : pToCheck) {
                if (c.equals(s)) {
                    count++;
                }
            }
        }
        Assert.assertEquals(count, pToCheck.length);
    }

    private void registerJolokiaMBeanServer() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.registerMBean(lookup,new ObjectName("jolokia:type=MBeanServer"));
    }

    private void unregisterJolokiaMBeanServer() throws MalformedObjectNameException, MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.unregisterMBean(new ObjectName("jolokia:type=MBeanServer"));
    }

    public void handleNotification(Notification notification, Object handback) {
        notificationCalled = true;
    }

    public static class TestLookup implements TestLookupMBean {

        MBeanServer server = MBeanServerFactory.newMBeanServer();

        public MBeanServer getJolokiaMBeanServer() {
            return server;
        }
    }

    public static interface TestLookupMBean {
        MBeanServer getJolokiaMBeanServer();
    }

    public static class Dummy implements DummyMBean {}
    public static interface DummyMBean {}

}
