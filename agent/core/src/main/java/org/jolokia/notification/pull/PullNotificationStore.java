package org.jolokia.notification.pull;

import java.util.*;

import javax.management.Notification;

import org.jolokia.notification.BackendRegistration;

/**
 * @author roland
 * @since 21.03.13
 */
public class PullNotificationStore {

    private Map<String,ClientStore> store;

    public void add(BackendRegistration pRegistration, Notification pNotification) {
        String key = pRegistration.getClient();
        ClientStore clientStore = store.get(key);
        if (clientStore == null) {
            clientStore = new ClientStore();
            store.put(key,clientStore);
        }
        clientStore.add(pRegistration,pNotification);
    }

    public List<Notification> pull(String pClientId, String pHandle) {
        ClientStore clientStore = store.get(pClientId);
        if (clientStore != null) {
            return clientStore.pull(pHandle);
        } else {
            return Collections.emptyList();
        }
    }

}
