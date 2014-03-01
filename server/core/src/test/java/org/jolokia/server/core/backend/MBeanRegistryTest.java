package org.jolokia.server.core.backend;

import javax.management.*;

import org.jolokia.server.core.service.impl.MBeanRegistry;
import org.testng.annotations.*;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 09.09.13
 */
public class MBeanRegistryTest {

    private MBeanRegistry registry;

    @BeforeMethod
    public void setUp() throws Exception {
        registry = new MBeanRegistry();
    }

    @AfterMethod
    public void tearDown() throws JMException {
        registry.destroy();
    }

    @Test
    public void registerAtMBeanServer() throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        ObjectName oName = new ObjectName("jolokia:name=Testy");
        Testy testy = new Testy(oName);

        ObjectName resName = registry.registerMBean(testy, "jolokia:name=Testy2");
        assertEquals(resName,oName);
    }

    interface TestyMBean {

    }

    class Testy implements TestyMBean,MBeanRegistration {

        ObjectName oName;

        Testy(ObjectName name) {
            this.oName = name;
        }

        public ObjectName preRegister(MBeanServer mBeanServer, ObjectName ignored) throws Exception {
            return oName;
        }

        public void postRegister(Boolean aBoolean) {

        }

        public void preDeregister() throws Exception {

        }

        public void postDeregister() {

        }
    }
}
