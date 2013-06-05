package org.jolokia.backend;

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.detector.ServerDetector;
import org.jolokia.detector.ServerHandle;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

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
        MBeanServers servers = new MBeanServers(getTestDetectors(), this);
        checkForServers(servers.getMBeanServers(), ManagementFactory.getPlatformMBeanServer(), ownServer);
        assertFalse(notificationCalled);

        servers.destroy();
        try {
            ManagementFactory.getPlatformMBeanServer().removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME,servers);
            fail("Exception should be thrown");
        } catch (ListenerNotFoundException exp) {

        }
    }



    @Test
    public void withJolokiaMBeanServerFromStart() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        registerJolokiaMBeanServer();
        MBeanServers servers = new MBeanServers(getTestDetectors(),this);
        checkForServers(servers.getMBeanServers(),ManagementFactory.getPlatformMBeanServer(), ownServer,lookup.getJolokiaMBeanServer());
        assertFalse(notificationCalled);
        assertEquals(lookup.getJolokiaMBeanServer(), servers.getJolokiaMBeanServer());
        unregisterJolokiaMBeanServer();
        servers.destroy();

    }

    @Test
    public void withJolokiaMBeanServerFromKickingInLater() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        MBeanServers servers = new MBeanServers(getTestDetectors(),this);
        registerJolokiaMBeanServer();

        MBeanServer jolokiaMBeanServer = lookup.getJolokiaMBeanServer();

        checkForServers(servers.getMBeanServers(),ManagementFactory.getPlatformMBeanServer(), ownServer,jolokiaMBeanServer);
        assertFalse(notificationCalled);
        jolokiaMBeanServer.registerMBean(new Dummy(), new ObjectName("dummy:type=dummy"));
        assertTrue(notificationCalled);
        assertEquals(jolokiaMBeanServer, servers.getJolokiaMBeanServer());
        jolokiaMBeanServer.unregisterMBean(new ObjectName("dummy:type=dummy"));
        unregisterJolokiaMBeanServer();
        servers.destroy();
    }

    @Test
    public void dump() {
        MBeanServers servers = new MBeanServers(getTestDetectors(),this);
        String dump = servers.dump();
        assertTrue(dump.contains("java.lang"));
        assertTrue(dump.contains("type=Memory"));
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
        assertEquals(count, pToCheck.length);
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

    private List<ServerDetector> getTestDetectors() {
        List<ServerDetector> ret = new ArrayList<ServerDetector>();
        ret.add(new ServerDetector() {

            public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
                return null;
            }

            public void addMBeanServers(Set<MBeanServerConnection> pMBeanServers) {
                pMBeanServers.add(ownServer);
            }
        });
        return ret;
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
