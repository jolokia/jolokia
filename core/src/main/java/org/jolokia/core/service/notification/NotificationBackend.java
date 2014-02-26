package org.jolokia.core.service.notification;

import java.util.Map;

import org.jolokia.core.service.JolokiaService;

/**
 * A notification backend which is responsible for the final delivery. This final
 * delivery is done by a callback which a backend needs to create out of a given
 * config. Also, a backend has a type which helps the notification handler to find
 * the proper backend
 *
 * @author roland
 * @since 18.03.13
 */
public interface NotificationBackend extends JolokiaService<NotificationBackend> {

    /**
     * Type of this backend
     *
     * @return type
     */
    String getNotifType();

    /**
     * Create a specific callback for the given configuration. This
     * callback will be called for every notification received
     *
     * @param pSubscription backend specific configuration which can be used for construction of
     *                      the callback
     * @return callback which is called on reception of a notification.
     */
    BackendCallback subscribe(NotificationSubscription pSubscription);

    /**
     * Unsubscribe for the given notification. Time to clean up
     *
     * @param pClientId client id
     * @param pHandle handle id for notification to unsubscribe
     */
    void unsubscribe(String pClientId, String pHandle);

    /**
     * Unregister a client
     *
     * @param pClientId client id
     */
    void unregister(String pClientId);

    /**
     * Return the global configuration specific for this backend. This can contain URL and
     * other information and is returned to the client during registration
     * The returned map must be serializable to JSON by Jolokia.
     *
     * @return the backend specific global configuration
     */
    Map<String,?> getConfig();
}
