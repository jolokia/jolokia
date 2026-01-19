package org.jolokia.service.jmx;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.*;

import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.service.impl.MBeanRegistry;
import org.jolokia.server.core.util.jmx.DefaultMBeanServerAccess;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.util.RequestType;
import org.testng.annotations.*;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 02.09.11
 */
public class MBeanServerHandlerTest {

    @SuppressWarnings("FieldCanBeLocal")
    private JolokiaRequest request;

    private MBeanRegistry handler;

    @BeforeMethod
    public void setup() throws MalformedObjectNameException, BadRequestException {
        TestDetector.reset();
        handler = new MBeanRegistry();
        request = new JolokiaRequestBuilder(RequestType.READ,"java.lang:type=Memory").attribute("HeapMemoryUsage").build();
    }

    @AfterMethod
    public void tearDown() throws JMException {
        if (handler != null) {
            handler.destroy();
        }
    }


    @Test(enabled = false)
    public void mbeanServers() throws JMException, IOException {
        checkMBeans(new ObjectName("java.lang:type=Memory"));

        /*
        String info = handler.mBeanServersInfo();
        assertTrue(info.contains("Platform MBeanServer"));
        assertTrue(info.contains("type=Memory"));
        */
    }

    @Test(enabled = false)
    public void mbeanRegistration() {
        //checkMBeans(new ObjectName(handler.getObjectName()));
    }

    private void checkMBeans(ObjectName oName) throws JMException, IOException {
        MBeanServerAccess servers = new DefaultMBeanServerAccess();
        final List<Boolean> result = new ArrayList<>();
        servers.each(oName, (pConn, pInstance) -> {
            // Throws an InstanceNotFoundException
            pConn.getObjectInstance(pInstance.getObjectName());
            result.add(pConn.isRegistered(pInstance.getObjectName()));
        });
        assertTrue(result.contains(Boolean.TRUE), "MBean not registered");
    }

    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void mbeanUnregistrationFailed1() throws JMException {
        handler.registerMBean(new Dummy(false, "test:type=dummy"));
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName("test:type=dummy"));
        handler.destroy();
    }

    @Test(expectedExceptions = JMException.class,expectedExceptionsMessageRegExp = ".*(dummy[12].*){2}.*")
    public void mbeanUnregistrationFailed2() throws JMException {
        handler.registerMBean(new Dummy(false, "test:type=dummy1"));
        handler.registerMBean(new Dummy(false, "test:type=dummy2"));
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName("test:type=dummy1"));
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName("test:type=dummy2"));
        handler.destroy();
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*not register.*")
    public void mbeanRegistrationFailed() throws JMException {
        handler.registerMBean(new Dummy(true, "test:type=dummy"));
    }


    // ===================================================================================================


    public interface DummyMBean {

    }
    private static class Dummy implements DummyMBean,MBeanRegistration {

        private final boolean throwException;
        private final String name;

        public Dummy(boolean b,String pName) {
            throwException = b;
            name = pName;
        }

        public ObjectName preRegister(MBeanServer server, ObjectName pName) throws Exception {
            if (throwException) {
                throw new RuntimeException();
            }
            return new ObjectName(name);
        }

        public void postRegister(Boolean registrationDone) {
        }

        public void preDeregister() {
        }

        public void postDeregister() {
        }
    }
}
