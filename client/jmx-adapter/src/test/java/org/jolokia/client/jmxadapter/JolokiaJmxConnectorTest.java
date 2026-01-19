/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.AttributeList;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class JolokiaJmxConnectorTest {

    private static final Logger LOG = Logger.getLogger(JolokiaJmxConnectorTest.class.getName());

    private int port;
    private JolokiaServer server;

    @BeforeClass
    public void startJVMAgent() throws Exception {
        port = EnvTestUtil.getFreePort();
        JolokiaServerConfig config = new JolokiaServerConfig(Map.of(
            "port", Integer.toString(port),
            "debug", "true"
        ));
        server = new JolokiaServer(config);
        server.start(false);
        LOG.info("Jolokia JVM Agent (test) started on port " + port);
    }

    @AfterClass
    public void stopJVMAgent() {
        server.stop();
        LOG.info("Jolokia JVM Agent (test) stopped");
    }

    @Test
    public void createConnectorUsingURI() throws Exception {
        JMXServiceURL serviceURL = new JMXServiceURL("jolokia+http", "127.0.0.1", port, "/jolokia");
        try (JMXConnector connector = JMXConnectorFactory.connect(serviceURL)) {
            assertTrue(connector instanceof JolokiaJmxConnector);
            assertTrue(connector.getMBeanServerConnection() instanceof RemoteJmxAdapter);
        }
    }

    @Test
    public void firstCaughtGetAttributesFromJConsole() throws Exception {
        JMXServiceURL serviceURL = new JMXServiceURL("jolokia+http", "127.0.0.1", port, "/jolokia");
        try (JMXConnector connector = JMXConnectorFactory.connect(serviceURL)) {
            ObjectName cl = ObjectName.getInstance("java.lang:type=ClassLoading");
            AttributeList attributes = connector.getMBeanServerConnection().getAttributes(cl, new String[]{"LoadedClassCount", "Fake"});
            assertEquals(attributes.size(), 2);
//            assertTrue(attributes.get(0) instanceof Number);
        }
    }

}
