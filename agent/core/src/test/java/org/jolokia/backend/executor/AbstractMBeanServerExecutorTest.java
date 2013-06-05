package org.jolokia.backend.executor;

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
import java.util.*;

import javax.management.*;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.01.13
 */
public class AbstractMBeanServerExecutorTest {

    TestExecutor executor;

    @BeforeMethod
    public void setup() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InterruptedException, InstanceNotFoundException, IOException {
        executor = new TestExecutor();
    }

    @Test
    public void jolokiaServer() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        assertNotNull(executor.getJolokiaMBeanServer());

        AbstractMBeanServerExecutor executorNull = new AbstractMBeanServerExecutor() {
            @Override
            protected Set<MBeanServerConnection> getMBeanServers() {
                return null;
            }
        };
        assertNull(executorNull.getJolokiaMBeanServer());
    }

    @Test
    public void eachNull() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, IOException, ReflectionException {
        executor.each(null, new MBeanServerExecutor.MBeanEachCallback() {
            public void callback(MBeanServerConnection pConn, ObjectName pName) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                checkHiddenMBeans(pConn, pName);
            }
        });
    }


    @Test
    public void eachObjectName() throws MalformedObjectNameException, MBeanException, IOException, ReflectionException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        for (final ObjectName name : new ObjectName[] { new ObjectName("test:type=one"), new ObjectName("test:type=two") }) {
            executor.each(name,new MBeanServerExecutor.MBeanEachCallback() {
                public void callback(MBeanServerConnection pConn, ObjectName pName) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                assertEquals(pName,name);
                    checkHiddenMBeans(pConn,pName);
                }
            });
        }
    }

    @Test
    public void updateChangeTest() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException, InterruptedException, IOException {
        executor.registerForMBeanNotifications();
        try {
            assertTrue(executor.hasMBeansListChangedSince(0),"updatedSince: When 0 is given, always return true");
            long time = currentTime();
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
            executor.destroy();
        }
    }

    @Test
    public void destroyWithoutPriorRegistration() throws NoSuchFieldException, IllegalAccessException {
        // Should always work, even when no registration has happened. Non exisiting listeners will be simplu ignored, since we didnt do any registration before
        executor.destroy();
    }

    private long currentTime() {
        return System.currentTimeMillis() / 1000;
    }

    @Test
    public void call() throws MalformedObjectNameException, MBeanException, InstanceAlreadyExistsException, NotCompliantMBeanException, IOException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException {
        String name = getAttribute(executor,"test:type=one","Name");
        assertEquals(name,"jolokia");
    }

    private String getAttribute(AbstractMBeanServerExecutor pExecutor, String name, String attribute) throws IOException, ReflectionException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException {
        return (String) pExecutor.call(new ObjectName(name),new MBeanServerExecutor.MBeanAction<Object>() {
                public Object execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException, AttributeNotFoundException {
                    return pConn.getAttribute(pName, (String) extraArgs[0]);
                }
            },attribute);
    }

    @Test(expectedExceptions = InstanceNotFoundException.class,expectedExceptionsMessageRegExp = ".*test:type=bla.*")
    public void callWithInvalidObjectName() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, IOException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException {
        getAttribute(executor,"test:type=bla","Name");
    }

    @Test(expectedExceptions = AttributeNotFoundException.class,expectedExceptionsMessageRegExp = ".*Bla.*")
    public void callWithInvalidAttributeName() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, IOException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException {
        getAttribute(executor,"test:type=one","Bla");
    }

    @Test
    public void queryNames() throws IOException, MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        Set<ObjectName> names = executor.queryNames(null);
        assertTrue(names.contains(new ObjectName("test:type=one")));
        assertTrue(names.contains(new ObjectName("test:type=two")));
        assertEquals(names.size(), 3);
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
    class TestExecutor extends AbstractMBeanServerExecutor {
        private       MBeanServer                jolokiaMBeanServer;
        private final Set<MBeanServerConnection> servers;
        private final MBeanServer                otherMBeanServer;

        private Testing jOne = new Testing(), oTwo = new Testing();
        private Hidden hidden = new Hidden();

        TestExecutor() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, InterruptedException, InstanceNotFoundException, IOException {

            jolokiaMBeanServer = MBeanServerFactory.newMBeanServer();
            otherMBeanServer = MBeanServerFactory.newMBeanServer();
            servers = new LinkedHashSet<MBeanServerConnection>(Arrays.asList(jolokiaMBeanServer, otherMBeanServer));

            jolokiaMBeanServer.registerMBean(jOne, new ObjectName("test:type=one"));
            otherMBeanServer.registerMBean(hidden, new ObjectName("test:type=one"));
            otherMBeanServer.registerMBean(oTwo, new ObjectName("test:type=two"));

        }

        @Override
        protected Set<MBeanServerConnection> getMBeanServers() {
            return servers;
        }

        @Override
        protected MBeanServerConnection getJolokiaMBeanServer() {
            return jolokiaMBeanServer;
        }

        void addMBean(int id) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
            MBeanServer server = getMBeanServerShuffled(id);
            server.registerMBean(new Testing(),new ObjectName("test:type=update,id=" + id));
        }

        void rmMBean(int id) throws MalformedObjectNameException, MBeanRegistrationException, InstanceNotFoundException {
            MBeanServer server = getMBeanServerShuffled(id);
            server.unregisterMBean(new ObjectName("test:type=update,id=" + id));
        }

        private MBeanServer getMBeanServerShuffled(int pId) {
            if (pId % 2 == 0) {
                return jolokiaMBeanServer;
            } else {
                return otherMBeanServer;
            }
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
}
