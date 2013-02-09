package org.jolokia.backend.executor;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.01.13
 */
public class AbstractMBeanServerExecutorTest {

    @Test
    public void jolokiaServer() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        AbstractMBeanServerExecutor executor = new TestExecutor();
        assertNotNull(executor.getJolokiaMBeanServer());

        executor = new AbstractMBeanServerExecutor() {
            @Override
            protected Set<MBeanServerConnection> getMBeanServers() {
                return null;
            }
        };
        assertNull(executor.getJolokiaMBeanServer());
    }

    @Test
    public void eachNull() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, IOException, ReflectionException {
        AbstractMBeanServerExecutor executor = new TestExecutor();

        executor.each(null,new MBeanServerExecutor.MBeanEachCallback() {
            public void callback(MBeanServerConnection pConn, ObjectName pName) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                checkHiddenMBeans(pConn, pName);
            }
        });
    }


    @Test
    public void eachObjectName() throws MalformedObjectNameException, MBeanException, IOException, ReflectionException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        AbstractMBeanServerExecutor executor = new TestExecutor();

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
    public void call() throws MalformedObjectNameException, MBeanException, InstanceAlreadyExistsException, NotCompliantMBeanException, IOException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException {
        AbstractMBeanServerExecutor executor = new TestExecutor();

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
        AbstractMBeanServerExecutor executor = new TestExecutor();

        getAttribute(executor,"test:type=bla","Name");
    }

    @Test(expectedExceptions = AttributeNotFoundException.class,expectedExceptionsMessageRegExp = ".*Bla.*")
    public void callWithInvalidAttributeName() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, IOException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException {
        AbstractMBeanServerExecutor executor = new TestExecutor();

        getAttribute(executor,"test:type=one","Bla");
    }

    @Test
    public void queryNames() throws IOException, MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        AbstractMBeanServerExecutor executor = new TestExecutor();

        Set<ObjectName> names = executor.queryNames(null);
        assertTrue(names.contains(new ObjectName("test:type=one")));
        assertTrue(names.contains(new ObjectName("test:type=two")));
        assertEquals(names.size(),3);
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

        TestExecutor() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {

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
