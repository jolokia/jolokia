package org.jolokia.service.jmx;

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
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.server.core.util.jmx.DefaultMBeanServerAccess;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 23.01.13
 */
public class DefaultMBeanServerAccessTest {

    TestAccess executor;

    @BeforeClass
    public void setup() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InterruptedException, InstanceNotFoundException, IOException {
        executor = new TestAccess(MBeanServerFactory.newMBeanServer());
    }

    @AfterClass
    public void cleanup() throws MalformedObjectNameException, InstanceNotFoundException, MBeanRegistrationException {
        executor.cleanup();
    }

    @Test
    public void eachNull() throws IOException, JMException {
        executor.each(null, (pConn, pInstance) -> {
            if (pConn != ManagementFactory.getPlatformMBeanServer()) {
                checkHiddenMBeans(pConn, pInstance.getObjectName());
            }
        });
    }


    @Test
    public void eachObjectName() throws JMException, IOException {
        for (final ObjectName name : new ObjectName[] { new ObjectName("test:type=one"), new ObjectName("test:type=two") }) {
            executor.each(name, (pConn, pInstance) -> {
                if (pConn != ManagementFactory.getPlatformMBeanServer()) {
                    assertEquals(pInstance.getObjectName(),name);
                    checkHiddenMBeans(pConn,pInstance.getObjectName());
                }
            });
        }
    }

    @Test
    public void updateChangeTest() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException, IOException {
        try {
            assertTrue(executor.hasMBeansListChangedSince(0),"updatedSince: When 0 is given, always return true");
            long time = currentTime() + 1;
            assertFalse(executor.hasMBeansListChangedSince(time), "No update yet");
            for (int id = 1; id <=2; id++) {
                time = currentTime();
                executor.addMBean(id);
                try {
                    assertTrue(executor.hasMBeansListChangedSince(0),"updatedSince: For 0, always return true");
                    assertTrue(executor.hasMBeansListChangedSince(time),"MBean has been added in the same second, hence it has been updated");
                    // Wait at a least a second
                    time = currentTime() + 1;
                    assertFalse(executor.hasMBeansListChangedSince(time),"No updated since the last call");
                } finally {
                    executor.rmMBean(id);
                }
            }
        } finally {
            executor.unregisterFromMBeanNotifications();
        }
    }

    @Test
    @Ignore
    public void destroyWithoutPriorRegistration() {
        // Should always work, even when no registration has happened. Non existing listeners will be simply ignored, since we didnt do any registration before
        executor.unregisterFromMBeanNotifications();
    }

    private long currentTime() {
        return System.currentTimeMillis() / 1000;
    }

    @Test
    public void call() throws JMException, IOException {
        String name = getAttribute(executor,"test:type=one","Name");
        assertEquals(name,"jolokia");
    }

    private String getAttribute(DefaultMBeanServerAccess pExecutor, String name, String attribute) throws IOException, JMException {
        return (String) pExecutor.call(new ObjectName(name), (pConn, pName, extraArgs) -> pConn.getAttribute(pName, (String) extraArgs[0]), attribute);
    }

    @Test(expectedExceptions = InstanceNotFoundException.class,expectedExceptionsMessageRegExp = ".*test:type=bla.*")
    public void callWithInvalidObjectName() throws JMException, IOException {
        getAttribute(executor,"test:type=bla","Name");
    }

    @Test(expectedExceptions = AttributeNotFoundException.class,expectedExceptionsMessageRegExp = ".*Bla.*")
    public void callWithInvalidAttributeName() throws JMException, IOException {
        getAttribute(executor, "test:type=one", "Bla");
    }

    @Test
    public void queryNames() throws IOException, MalformedObjectNameException {
        Set<ObjectName> names = executor.queryNames(null);
        assertTrue(names.contains(new ObjectName("test:type=one")));
        assertTrue(names.contains(new ObjectName("test:type=two")));
    }

    private void checkHiddenMBeans(MBeanServerConnection pConn, ObjectName pName) throws MBeanException, InstanceNotFoundException, ReflectionException, IOException {
        try {
            if (!pName.equals(new ObjectName("JMImplementation:type=MBeanServerDelegate"))) {
                assertEquals(pConn.getAttribute(pName,"Name"),"jolokia");
            }
        } catch (AttributeNotFoundException e) {
            fail("Name should be accessible on all MBeans");
        } catch (MalformedObjectNameException e) {
            // wont happen
        }

        try {
            pConn.getAttribute(pName, "Age");
            fail("No access to hidden MBean allowed");
        } catch (AttributeNotFoundException exp) {
            // Expected
        }
    }
    static class TestAccess extends DefaultMBeanServerAccess {

        private final MBeanServer mbeanServer;

        TestAccess(MBeanServer pMBeanServer) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
            super(new HashSet<>(Collections.singletonList(pMBeanServer)));
            mbeanServer = pMBeanServer;

            MBeanServer jolokiaServer = (MBeanServer) getJolokiaMBeanServer();
            Testing jOne = new Testing();
            jolokiaServer.registerMBean(jOne, new ObjectName("test:type=one"));
            Hidden hidden = new Hidden();
            mbeanServer.registerMBean(hidden, new ObjectName("test:type=one"));
            Testing oTwo = new Testing();
            mbeanServer.registerMBean(oTwo, new ObjectName("test:type=two"));
        }

        void addMBean(int id) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
            MBeanServer server = (MBeanServer) getMBeanServerShuffled(id);
            server.registerMBean(new Testing(),new ObjectName("test:type=update,id=" + id));
        }

        void rmMBean(int id) throws MalformedObjectNameException, MBeanRegistrationException, InstanceNotFoundException, IOException {
            MBeanServerConnection server = getMBeanServerShuffled(id);
            server.unregisterMBean(new ObjectName("test:type=update,id=" + id));
        }

        private MBeanServerConnection getMBeanServerShuffled(int pId) {
            if (pId % 2 == 0) {
                return getJolokiaMBeanServer();
            } else {
                return mbeanServer;
            }
        }

        public void cleanup() throws MalformedObjectNameException, MBeanRegistrationException, InstanceNotFoundException {
            MBeanServer jolokiaServer = (MBeanServer) getJolokiaMBeanServer();
            jolokiaServer.unregisterMBean(new ObjectName("test:type=one"));
            mbeanServer.unregisterMBean(new ObjectName("test:type=one"));
            mbeanServer.unregisterMBean(new ObjectName("test:type=two"));
        }
    }

    public interface TestingMBean {
        String getName();
    }

    public static class Testing implements TestingMBean {

        public String getName() {
            return "jolokia";
        }
    }

    public interface HiddenMBean {
        int getAge();
    }

    public static class Hidden implements HiddenMBean {

        public int getAge() {
            return 1;
        }

    }

    @BeforeClass
    public void registerJolokiaMBeanServer() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.registerMBean(new TestLookup(),new ObjectName("jolokia:type=MBeanServer"));
    }
    @AfterClass
    public void unregisterJolokiaMBeanServer() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.unregisterMBean(new ObjectName("jolokia:type=MBeanServer"));
    }

    public static class TestLookup implements TestLookupMBean {

        MBeanServer server = MBeanServerFactory.newMBeanServer();

        public MBeanServer getJolokiaMBeanServer() {
            return server;
        }
    }

    public interface TestLookupMBean {
        MBeanServer getJolokiaMBeanServer();
    }

}
