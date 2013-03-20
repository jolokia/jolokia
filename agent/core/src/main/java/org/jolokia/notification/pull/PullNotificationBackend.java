package org.jolokia.notification.pull;

import javax.management.Notification;

import org.jolokia.notification.BackendCallback;
import org.jolokia.notification.NotificationBackend;

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

    /** {@inheritDoc} */
    public BackendCallback getBackendCallback() {

        return new BackendCallback() {
            /** {@inheritDoc} */
            public void handleNotification(Notification notification, Object handback) {
                System.out.println(">>>> Notif-received: " + notification.getType() + ", "
                                   + notification.getMessage() + ", handback: " + handback);
            }
        };
    }
}
