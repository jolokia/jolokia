package org.jolokia.agent.core.backend;

import javax.management.*;

import org.jolokia.agent.core.history.History;
import org.jolokia.agent.core.service.impl.MBeanRegistry;
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
        History history = new History(null,"jolokia:type=Config");
        ObjectName oName = new ObjectName("jolokia:type=Config");

        ObjectName resName = registry.registerMBean(history, "jolokia:type=Config");
        assertEquals(resName,oName);

    }

}
