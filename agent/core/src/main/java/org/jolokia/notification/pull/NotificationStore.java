package org.jolokia.notification.pull;

import java.util.LinkedList;
import java.util.List;

import javax.management.Notification;

import org.jolokia.notification.BackendRegistration;

/**
 * @author roland
 * @since 21.03.13
 */
public class NotificationStore {

    private int maxEntries;

    List<Notification> entries;

    public NotificationStore(BackendRegistration pRegistration) {
        entries = new LinkedList<Notification>();
    }

    public void add(Notification pNotification) {
        entries.add(pNotification);
    }

    public List<Notification> getNotifications() {
        return entries;
    }
}
