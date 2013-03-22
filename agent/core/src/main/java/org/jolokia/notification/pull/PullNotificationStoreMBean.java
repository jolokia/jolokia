package org.jolokia.notification.pull;

import java.util.List;

import javax.management.Notification;

/**
 * MBean for accessing pull requests.
 *
 * @author roland
 * @since 21.03.13
 */
public interface PullNotificationStoreMBean {

    /**
     * Get notification for client and a certain subscription. This will also
     * clear out the notification store. The list returned contains the
     * notifications ordered by sequence number (lower sequence numbers first).
     *
     * @param pClientId client id
     * @param pHandle the subscription handle
     * @return list of stored notifications or an empty list if there are no notifications.
     */
    List<Notification> pull(String pClientId, String pHandle);
}
