package org.jolokia.notification;

import java.util.Map;

/**
 * A notification backend which is responsible for the final delivery. This final
 * delivery is done by a callback which a backend needs to create out of a given
 * config. Also, a backend has a type which helps the notification handler to find
 * the proper backend
 *
 * @author roland
 * @since 18.03.13
 */
public interface NotificationBackend {

    /**
     * Type of this backend
     *
     * @return type
     */
    String getType();

    /**
     * Create a specific callback for the given configuration. This
     * callback will be called for every notification received
     *
     * @return callback which is stored in the {@link org.jolokia.notification.admin.NotificationListenerDelegate} for
     *         triggering when the appropriate notification arrives.
     */
    BackendCallback getBackendCallback();

    /**
     * Return the configuration specific for this backend. This can contain URL and
     * other information. The returned map must be serializable to JSON by Jolokia.
     *
     * @return the backend specific configuration
     */
    Map<String,?> getConfig();
}
