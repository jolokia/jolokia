/*
 * Copyright 2009-2013  Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.jmx;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.*;

import org.jolokia.backend.MBeanServerHandler;
import org.jolokia.backend.MBeanServerHandlerMBean;
import org.jolokia.util.ConfigKey;
import org.jolokia.util.LogHandler;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.01.13
 */
public class JolokiaMBeanServerUtilTest implements LogHandler {

    MBeanServerHandler handler;

    @BeforeTest
    public void setup() {
        Map<ConfigKey, String> config = new HashMap<ConfigKey, String>();
        config.put(ConfigKey.DEBUG, "true");
        handler = new MBeanServerHandler(config, this);
    }

    @AfterTest
    public void tearDown() throws JMException {
        handler.unregisterMBeans();
    }

    @Test
    public void handlerBeanOnPlatformServer() throws MalformedObjectNameException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        assertTrue(server.isRegistered(new ObjectName(MBeanServerHandlerMBean.OBJECT_NAME)));
    }

    @Test
    public void checkNotRegistered() throws MalformedObjectNameException {
        MBeanServer jolokiaServer = JolokiaMBeanServerUtil.getJolokiaMBeanServer();
        assertNotEquals(ManagementFactory.getPlatformMBeanServer(), jolokiaServer);
        for (MBeanServer server : MBeanServerFactory.findMBeanServer(null)) {
            assertNotEquals(server, jolokiaServer);
        }
    }

    @Test
    public void registerMBean() throws MalformedObjectNameException, NotCompliantMBeanException,
                                       InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer jolokiaServer = JolokiaMBeanServerUtil.getJolokiaMBeanServer();

        Dummy test = new Dummy();
        ObjectName name = new ObjectName("jolokia.test:name=test");
        JolokiaMBeanServerUtil.registerMBean(test, name);
        assertTrue(jolokiaServer.isRegistered(name));
        JolokiaMBeanServerUtil.unregisterMBean(name);
        assertFalse(jolokiaServer.isRegistered(name));
    }

    public void debug(String message) {
    }

    public void info(String message) {
    }

    public void error(String message, Throwable t) {
    }

    interface DummyMBean {

    }
    private class Dummy implements DummyMBean {
    }
}
