package org.jolokia.notification.pull;

import javax.management.Notification;

import org.jolokia.notification.BackendCallback;
import org.jolokia.notification.NotificationBackend;

/**
 * @author roland
 * @since 20.03.13
 */
public class PullNotificationBackend implements NotificationBackend {

    public String getType() {
        return "pull";
    }

    public BackendCallback getBackendCallback() {
        return new BackendCallback() {
            public void handleNotification(Notification notification, Object handback) {
                System.out.println(">>>> Notif-received: " + notification.getType() + ", "
                                   + notification.getMessage() + ", handback: " + handback);
            }
        };
    }
}
