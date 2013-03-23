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

import java.util.List;

import javax.management.Notification;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 23.03.13
 */
public class ClientStoreTest {
    @Test
    public void testAddAndPull() throws Exception {
        ClientStore clientStore = new ClientStore("client",5);
        Notification notif = new Notification("test.test", this, 1);
        clientStore.add(new TestNotificationSubscription("handle"),notif);
        List<Notification> notifs = clientStore.pull("unknown");
        assertEquals(notifs.size(),0);

        notifs = clientStore.pull("handle");
        assertEquals(notifs.size(),1);
        assertEquals(notifs.get(0),notif);
        notifs = clientStore.pull("handle");
        assertEquals(notifs.size(),0);
    }

    @Test
    public void testRemoveSubscription() throws Exception {
        ClientStore clientStore = new ClientStore("client",5);
        Notification notif = new Notification("test.test", this, 1);
        clientStore.add(new TestNotificationSubscription("handle"),notif);
        clientStore.removeSubscription("handle");
        List<Notification> notifs = clientStore.pull("handle");
        assertEquals(notifs.size(),0);
    }
}
