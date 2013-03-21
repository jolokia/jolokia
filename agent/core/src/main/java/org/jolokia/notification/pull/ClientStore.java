package org.jolokia.notification.pull;

import java.util.*;

import javax.management.Notification;

import org.jolokia.notification.BackendRegistration;

/**
 * @author roland
 * @since 21.03.13
 */
public class ClientStore  {

    Map<String,NotificationStore> store;

    public void add(BackendRegistration pRegistration, Notification pNotification) {
        String key = pRegistration.getHandle();
        NotificationStore notifStore = store.get(key);
        if (notifStore == null) {
            notifStore = new NotificationStore(pRegistration);
            store.put(key,notifStore);
        }
        notifStore.add(pNotification);
    }

    public List<Notification> pull(String pHandle) {
        NotificationStore notificationStore = store.get(pHandle);
        if (notificationStore != null) {
            return notificationStore.getNotifications();
        } else {
            return Collections.emptyList();
        }

    }
}
