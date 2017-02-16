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
    private ObjectName mBean;

    // Filter used
    private List<String> filter;

    // A handback object
    private Object handback;

    // Extra configuration used for the registration
    private Map<String, ?> config;

    // Used for updating freshness
    private NotificationListenerDelegate delegate;

    // Client id
    private Client client;

    // Registration handle
    private String handle;

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
