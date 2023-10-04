package org.jolokia.service.jmx.handler.notification;

import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.jolokia.server.core.service.notification.Client;
import org.jolokia.server.core.service.notification.NotificationSubscription;
import org.jolokia.server.core.request.notification.AddCommand;

/**
 * Class holding registration information
 *
 * @author roland
 * @since 21.03.13
 */
class NotificationSubscriptionImpl implements NotificationSubscription {

    // MBean on which the notification is registered
    private final ObjectName mBean;

    // Filter used
    private final List<String> filter;

    // A handback object
    private final Object handback;

    // Extra configuration used for the registration
    private final Map<String, ?> config;

    // Used for updating freshness
    private final NotificationListenerDelegate delegate;

    // Client id
    private final Client client;

    // Registration handle
    private final String handle;

    /**
     * Registration used when registering at a notification backend
     *
     * @param pHandle handle of the registration
     * @param pCommand original command used for registration
     * @param pListenerDelegate delegate for updating freshness
     */
    NotificationSubscriptionImpl(String pHandle, AddCommand pCommand, Client pClient, NotificationListenerDelegate pListenerDelegate) {
        mBean = pCommand.getObjectName();
        filter = pCommand.getFilter();
        handback = pCommand.getHandback();
        config = pCommand.getConfig();
        delegate = pListenerDelegate;
        client = pClient;
        handle = pHandle;
    }

    /** {@inheritDoc} */
    public void ping() {
        delegate.refresh(client.getId());
    }

    /** {@inheritDoc} */
    public Client getClient() {
        return client;
    }

    /** {@inheritDoc} */
    public String getHandle() {
        return handle;
    }

    /** {@inheritDoc} */
    public ObjectName getMBean() {
        return mBean;
    }

    /** {@inheritDoc} */
    public List<String> getFilter() {
        return filter;
    }

    /** {@inheritDoc} */
    public Object getHandback() {
        return handback;
    }

    /** {@inheritDoc} */
    public Map<String, ?> getConfig() {
        return config;
    }
}
