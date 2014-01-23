package org.jolokia.service.notification;

import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

/**
 * Interface for offering registration information to the backend and
 * means for refreshing a client.
 *
 * @author roland
 * @since 21.03.13
 */

public interface NotificationSubscription {

    /**
     * Method which should be called if a client
     * could be reached. This will update the freshness
     * of the client itself. Clients which are not
     * active within a certain period are considered as
     * 'stale' and are removed.
     */
    void ping();

    /**
     * Get the client id which registered this notification
     *
     * @return client id
     */
    String getClient();

    /**
     * Get the handle of the registration. Required for retreiving
     * the stored notifications
     */
    String getHandle();

    /**
     * MBean on which the notification listener is registered
     *
     * @return mbean name
     */
    ObjectName getMBean();

    /**
     * List of filters on notification types, can be null
     *
     * @return filters
     */
    List<String> getFilter();

    /**
     * Optional handback object (can be null)
     *
     * @return handback or null;
     */
    Object getHandback();

    /**
     * Extra configuration for this registration request (can be null or empty)
     *
     * @return config map or null
     */
    Map<String, ?> getConfig();
}
