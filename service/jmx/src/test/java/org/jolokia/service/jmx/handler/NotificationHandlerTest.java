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

package org.jolokia.service.jmx.handler;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaNotificationRequest;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.request.notification.NotificationCommandType;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.*;

import static org.easymock.EasyMock.createMock;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 20.03.13
 */
public class NotificationHandlerTest extends BaseHandlerTest {

    private NotificationHandler handler;

    TestJolokiaContext ctx;

    @BeforeMethod
    public void setUp() {
        ctx = new TestJolokiaContext.Builder().config(ConfigKey.AGENT_ID,"test")
                                              .build();

        handler = new NotificationHandler();
        handler.init(ctx,null);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        handler.destroy();
    }

    @Test
    public void testSimple() throws Exception {
        assertEquals(handler.getType(), RequestType.NOTIFICATION);
        JolokiaNotificationRequest request = createRequest();
        // No exception for now
        handler.checkForRestriction(request);
        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        handler.doHandleAllServerRequest(getMBeanServerManager(connection), request, null);
    }

    private JolokiaNotificationRequest createRequest() throws MalformedObjectNameException {
        return new JolokiaRequestBuilder(RequestType.NOTIFICATION).
                command(NotificationCommandType.REGISTER).build();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testUnsupported() throws Exception {
        JolokiaNotificationRequest request = createRequest();
        assertTrue(handler.handleAllServersAtOnce(request));
        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        handler.doHandleSingleServerRequest(connection, request);
    }
}
