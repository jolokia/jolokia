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

package org.jolokia.agent.service.pullnotif;

import javax.management.Notification;

import org.jolokia.agent.core.service.notification.NotificationSubscription;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 23.03.13
 */
public class NotificationStoreTest {
    @Test
    public void testSimple() throws Exception {
        Object handback = new Object();
        NotificationSubscription subcr = createNotificationSubscription(handback);
        NotificationStore store = new NotificationStore(subcr,1);
        store.add(new Notification("test.test",this,1));
        assertEquals(store.getDropped(),0);
        store.add(new Notification("test.test2",this,2));
        assertEquals(store.getDropped(),1);
        NotificationResult notifs = store.fetchAndClear();
        assertEquals(notifs.getNotifications().size(),1);
        assertEquals(notifs.getDropped(),1);
        assertEquals(notifs.getHandback(),handback);
        assertEquals(notifs.getNotifications().get(0).getSequenceNumber(),2);
        assertEquals(notifs.getNotifications().get(0).getType(),"test.test2");
        notifs = store.fetchAndClear();
        assertEquals(notifs.getNotifications().size(),0);
        assertEquals(notifs.getDropped(),0);
    }

    private NotificationSubscription createNotificationSubscription(Object handback) {
        return new TestNotificationSubscription("test","1",handback);
    }

}
