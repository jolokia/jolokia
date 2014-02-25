package org.jolokia.jsr160.test;

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
import java.util.Map;

import javax.management.remote.*;
import javax.naming.Context;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Dummy connector which relinks the remote call to a local call.
 *
 * @author roland
 * @since 28.09.11
 */
public class ClientProvider implements JMXConnectorProvider {
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        JMXConnector connector = createMock(JMXConnector.class);
        connector.connect();
        expectLastCall().anyTimes();
        connector.close();
        expectLastCall().anyTimes();
        expect(connector.getMBeanServerConnection()).andReturn(ManagementFactory.getPlatformMBeanServer());
        replay(connector);
        String user = System.getProperty("TEST_WITH_USER");
        if (user != null) {
            assertEquals(environment.get(Context.SECURITY_PRINCIPAL),user);
            assertNotNull(environment.get(Context.SECURITY_CREDENTIALS));
        }
        return connector;
    }
}
