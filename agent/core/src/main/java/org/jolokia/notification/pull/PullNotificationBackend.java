package org.jolokia.notification.pull;

import java.util.Map;

import javax.management.Notification;

import org.jolokia.notification.BackendCallback;
import org.jolokia.notification.NotificationBackend;
import org.json.simple.JSONObject;

/**
 * Dummy implementation
 *
 * @author roland
 * @since 20.03.13
 */
public class PullNotificationBackend implements NotificationBackend {

    /** {@inheritDoc} */
    public String getType() {
        return "pull";
    }

    /** {@inheritDoc}
     * @param pConfig*/
    public BackendCallback getBackendCallback(Map<String, ?> pConfig) {

        return new BackendCallback() {
            /** {@inheritDoc} */
            public void handleNotification(Notification notification, Object handback) {
                System.out.println(">>>> Notif-received: " + notification.getType() + ", "
                                   + notification.getMessage() + ", handback: " + handback);
            }
        };
    }

    public Map<String, ?> getConfig() {
        JSONObject ret = new JSONObject();
        ret.put("store","jolokia:type=NotificationStore");
        return ret;
    }
}
