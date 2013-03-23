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

package org.jolokia.it.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.*;

/**
 * @author roland
 * @since 23.03.13
 */
public class Chat extends NotificationBroadcasterSupport implements ChatMBean  {

    public static final String NOTIF_TYPE = "jolokia.chat";
    private final ObjectName source;
    private AtomicInteger seqNumber = new AtomicInteger();

    public Chat() throws MalformedObjectNameException {
        super(new MBeanNotificationInfo(
               new String[] {NOTIF_TYPE},
               Notification.class.getName(),
               "Chat notification"
        ));
        source = new ObjectName("jolokia.it:type=Chat");
    }

    public void message(String who, String message) throws MalformedObjectNameException {
        Notification notification = new Notification(NOTIF_TYPE,source,seqNumber.getAndIncrement());

        Map<String,String> data = new HashMap<String, String>();
        data.put("user",who);
        data.put("message",message);
        notification.setUserData(data);

        sendNotification(notification);
    }
}
