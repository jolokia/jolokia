package org.jolokia.agent.service.pullnotif;

import java.util.*;

import javax.management.Notification;

import org.jolokia.agent.core.service.notification.NotificationSubscription;

/**
 * Implementation of store holding notifications which can be pulled via JMX.
 *
 * @author roland
 * @since 21.03.13
 */
public class PullNotificationStore implements PullNotificationStoreMBean {

    // store holding the notifications
    private Map<String,ClientStore> store;

    // maximum entries per subscription
    private int maxEntries;

    /**
     * Initialize the store
     *
     * @param pMaxEntries maximum entries per subscriptions
     */
    PullNotificationStore(int pMaxEntries) {
        store = new HashMap<String, ClientStore>();
        maxEntries = pMaxEntries;
    }

    /**
     * JMX exposed method for pulling out notifications. The list of notifications
     * is cleared afterwards.
     *
     * @param pClientId client id
     * @param pHandle the subscription handle
     * @return notifications stored for the client or null
     *         if no notification are stored currently.
     */
    public NotificationResult pull(String pClientId, String pHandle) {
        ClientStore clientStore = store.get(pClientId);

        if (clientStore != null) {
            return clientStore.pull(pHandle);
        } else {
            return null;
        }
    }

    /**
     * Add a new notification to this store.
     *
     * @param pRegistration registration obtained from the notification subsystem
     * @param pNotification the notification to add
     */
    void add(NotificationSubscription pRegistration, Notification pNotification) {
        String key = pRegistration.getClient();
        ClientStore clientStore = store.get(key);
        if (clientStore == null) {
            clientStore = new ClientStore(maxEntries);
            store.put(key,clientStore);
        }
        clientStore.add(pRegistration,pNotification);
    }

    /**
     * Remove subscription
     * @param pClientId client id
     * @param pHandle notificion subscription handle
     */
    void removeSubscription(String pClientId, String pHandle) {
        ClientStore clientStore = store.get(pClientId);
        if (clientStore != null) {
            clientStore.removeSubscription(pHandle);
        }
    }

    /**
     * Remove the given client completely from the store
     * @param pClientId client to remove
     */
    void removeClient(String pClientId) {
        store.remove(pClientId);
    }
}
