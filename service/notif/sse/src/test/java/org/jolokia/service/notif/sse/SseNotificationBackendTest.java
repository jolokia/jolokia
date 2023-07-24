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

package org.jolokia.service.notif.sse;

import java.util.UUID;

import javax.management.Notification;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.notification.BackendCallback;
import org.jolokia.server.core.service.notification.Client;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.*;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 23.03.13
 */

@Test
public class SseNotificationBackendTest {

    private SseNotificationBackend backend;
    private TestJolokiaContext context;

    @BeforeMethod
    public void setUp() throws Exception {
        context = new TestJolokiaContext.Builder()
                .config(ConfigKey.AGENT_ID,"test")
                .build();
        backend = new SseNotificationBackend(0);
        backend.init(context);
        assertEquals(backend.getNotifType(), "sse");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        backend.destroy();
        context.destroy();
    }

    @Test
    public void testSubscription() throws Exception {
       String client = UUID.randomUUID().toString();
       String handle = "1";

       BackendCallback cb = backend.subscribe(new TestNotificationSubscription(client, handle));
       Object handback = new Object();
       Notification notification = new Notification("test.test", this, 1);
       cb.handleNotification(notification, handback);
   }

    @Test
    public void testUnsubscribe() throws Exception {
        String client = UUID.randomUUID().toString();
        String handle = "1";
        TestNotificationSubscription sbc =
                new TestNotificationSubscription(client,handle);
        backend.unsubscribe(client, handle);
        BackendCallback callback = backend.subscribe(sbc);

        Object handback = new Object();
        Notification notification = new Notification("test.test", this, 1);
        callback.handleNotification(notification,handback);

        backend.unsubscribe(client, handle);
    }

    @Test
    public void testUnregister() throws Exception {
        String client = UUID.randomUUID().toString();
        String handle = "1";
        TestNotificationSubscription sbc =
                new TestNotificationSubscription(client,handle);
        BackendCallback callback = backend.subscribe(sbc);

        Object handback = new Object();
        Notification notification = new Notification("test.test", this, 1);
        callback.handleNotification(notification,handback);

        backend.unregister(new Client(client));
    }
}
