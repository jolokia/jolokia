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

package org.jolokia.notification.pull;

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.notification.BackendCallback;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.03.13
 */
public class PullNotificationBackendTest {

    private PullNotificationBackend backend;

    @BeforeMethod
    public void setUp() throws Exception {
        backend = new PullNotificationBackend("test");
        assertEquals(backend.getType(),"pull");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        backend.destroy();
    }

    @Test
    public void testConfig() throws Exception {
        JSONObject cfg = (JSONObject) backend.getConfig();
        assertEquals(cfg.get("store"),"jolokia:type=NotificationStore,agent=test");
        assertTrue(cfg.containsKey("maxEntries"));
    }

   @Test
   public void testSubscription() throws Exception {
       String client = UUID.randomUUID().toString();
       String handle = "1";

       BackendCallback cb = backend.subscribe(new TestNotificationSubscription(client, handle));
       Object handback = new Object();
       Notification notification = new Notification("test.test", this, 1);
       cb.handleNotification(notification,handback);

       List<Notification> notifs = jmxPull(client, handle);
       assertEquals(notifs.size(),1);
       assertEquals(notifs.get(0),notification);
       notifs = jmxPull(client, handle);
       assertEquals(notifs.size(),0);
   }

    @Test
    public void testUnsubscribe() throws Exception {
        String client = UUID.randomUUID().toString();
        String handle = "1";
        TestNotificationSubscription sbc =
                new TestNotificationSubscription(client,handle);
        backend.unsubscribe(client,handle);
        BackendCallback callback = backend.subscribe(sbc);

        Object handback = new Object();
        Notification notification = new Notification("test.test", this, 1);
        callback.handleNotification(notification,handback);

        backend.unsubscribe(client,handle);

        List<Notification> notifs = jmxPull(client, handle);
        assertEquals(notifs.size(),0);
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

        backend.unregister(client);

        List<Notification> notifs = jmxPull(client, handle);
        assertEquals(notifs.size(),0);
    }

    private List<Notification> jmxPull(String pClient, String pHandle) throws MalformedObjectNameException, InstanceNotFoundException, MBeanException, ReflectionException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Map cfg = backend.getConfig();
        ObjectName name = new ObjectName((String) cfg.get("store"));
        return (List<Notification>) server.invoke(name,"pull",new Object[] {pClient, pHandle}, new String[] { String.class.getName(), String.class.getName()});
    }
}
