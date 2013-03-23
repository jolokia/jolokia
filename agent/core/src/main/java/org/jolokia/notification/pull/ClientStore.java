package org.jolokia.notification.pull;

import java.util.*;

import javax.management.Notification;

import org.jolokia.notification.NotificationSubscription;

/**
 * A Client store is responsible for holding all notifications
 * for a single client.
 *
 * @author roland
 * @since 21.03.13
 */
public class ClientStore  {

    // association client to notifications
    private Map<String,NotificationStore> store;

    // Max notification entries to hold
    private int maxEntries;

    /**
     * Init with a maimal entry limit
     *
     * @param pMaxEntries max entries to hold
     */
    ClientStore(int pMaxEntries) {
        maxEntries = pMaxEntries;
        store = new HashMap<String, NotificationStore>();
    }

    /**
     * Add a notification for this client
     *
     * @param pSubscription the subscription handle
     * @param pNotification the notification to add
     */
    void add(NotificationSubscription pSubscription, Notification pNotification) {
        NotificationStore notifStore = store.get(pSubscription.getHandle());
        if (notifStore == null) {
            notifStore = new NotificationStore(pSubscription,maxEntries);
            store.put(pSubscription.getHandle(),notifStore);
        }
        notifStore.add(pNotification);
    }

    /**
     * Pull off notification for this client and a given handle. This will
     * also clear all stored notification.
     *
     * @param pHandle subscription handle
     * @return notification result or null
     */
    NotificationResult pull(String pHandle) {
        NotificationStore notificationStore = store.get(pHandle);
        if (notificationStore != null) {
            return notificationStore.fetchAndClear();
        } else {
            return null;
        }
    }

    /**
     * Remove subscription
     *
     * @param pHandle notification handle
     */
    void removeSubscription(String pHandle) {
        store.remove(pHandle);
    }
}
