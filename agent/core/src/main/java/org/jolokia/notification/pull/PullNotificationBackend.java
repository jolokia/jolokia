package org.jolokia.notification.pull;

import java.util.Map;

import javax.management.Notification;

import org.jolokia.notification.*;
import org.json.simple.JSONObject;

/**
 * Dummy implementation
 *
 * @author roland
 * @since 20.03.13
 */
public class PullNotificationBackend implements NotificationBackend {

    private PullNotificationStore store;

    private String jolokaiId;

    public PullNotificationBackend(String pId) {
        jolokaiId = pId;
        store = new PullNotificationStore();
    }

    /** {@inheritDoc} */
    public String getType() {
        return "pull";
    }

    public BackendCallback getBackendCallback(final BackendRegistration pRegistration) {
        return new BackendCallback() {
            public void handleNotification(Notification notification, Object handback) {
                store.add(pRegistration,notification);
            }
        };
    }

    public Map<String, ?> getConfig() {
        JSONObject ret = new JSONObject();
        ret.put("store","jolokia:type=NotificationStore,id=" + jolokaiId);
        return ret;
    }
}
