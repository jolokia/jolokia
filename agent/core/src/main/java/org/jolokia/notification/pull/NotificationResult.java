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

/**
 * Result class holding notifications, handback objects and the number of
 * dropped notifications.
 *
 * @author roland
 * @since 23.03.13
 */
class NotificationResult {

    // List of notifications
    private List<Notification> notifications;

    // Handback object given during subscription
    private Object handback;

    // Number of notifications dropped since the last pull
    private int dropped;

    NotificationResult(List<Notification> pNotifications, Object pHandback, int pDropped) {
        notifications = pNotifications;
        handback = pHandback;
        dropped = pDropped;
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
}
