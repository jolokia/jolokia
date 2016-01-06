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

package org.jolokia.it.core.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.*;

/**
 * A sample MBean throwing notification on demand
 */
public class Chat extends NotificationBroadcasterSupport implements ChatMBean  {

    // Sequence number uniquely identifying a notification
    private AtomicInteger seqNumber = new AtomicInteger();

    /**
     * Constructor preparing the meta data for the base class
     * {@link NotificationBroadcasterSupport}.
     *
     * @throws MalformedObjectNameException
     */
    public Chat() throws MalformedObjectNameException {
        super(new MBeanNotificationInfo(
                new String[] {"jolokia.chat"},
                Notification.class.getName(),
                "Chat notification"));
    }

    /**
     * JMX exposed operation for dispatching a message to all registered
     * notification listeners. This is the only method defined in the standard-MBean
     * interface {@link ChatMBean}.
     *
     * @param who who is sending the message
     * @param message the message itself
     */
    public void message(String who, String message) {
        // Create notification
        Notification notification =
                new Notification("jolokia.chat", this, seqNumber.getAndIncrement());

        // Prepare and set payload for listeners
        Map<String,String> data = new HashMap<String, String>();
        data.put("user",who);
        data.put("message",message);
        notification.setUserData(data);

        // Fire notification to all listeners
        sendNotification(notification);
    }
}
