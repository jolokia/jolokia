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

package org.jolokia.server.core.service.notification;

import java.util.List;

import javax.management.Notification;

/**
 * Result class holding notifications, handback objects and the number of
 * dropped notifications.
 *
 * @author roland
 * @since 23.03.13
 */
public class NotificationResult {

    // Notification handle
    private final String handle;

    // List of notifications
    private final List<Notification> notifications;

    // Handback object given during subscription
    private final Object handback;

    // Number of notifications dropped since the last pull
    private final int dropped;

    public NotificationResult(String pHandle, List<Notification> pNotifications, Object pHandback, int pDropped) {
        notifications = pNotifications;
        handback = pHandback;
        dropped = pDropped;
        handle = pHandle;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public Object getHandback() {
        return handback;
    }

    public int getDropped() {
        return dropped;
    }

    public String getHandle() {
        return handle;
    }
}
