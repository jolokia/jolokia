package org.jolokia.handler.notification;

import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.jolokia.notification.NotificationSubscription;
import org.jolokia.request.notification.AddCommand;

/**
 * Class holding registration information
 *
 * @author roland
 * @since 21.03.13
 */
public class NotificationSubscriptionImpl implements NotificationSubscription {

    // MBean on which the notification is registered
    private ObjectName mBean;

    // Filter used
    private List<String> filter;

    // A handback object
    private Object handback;

    // Extra onfiguration used for the registration
    private Map<String, ?> config;

    // Used for updating freshness
    private NotificationListenerDelegate delegate;

    // Client id
    private String                       client;

    // Registration handle
    private String                       handle;

    /**
     * Registration used when registering at a notification backend
     *
     * @param pHandle handle of the registration
     * @param pCommand original command used for registration
     * @param pListenerDelegate delegate for updating freshness
     */
    NotificationSubscriptionImpl(String pHandle, AddCommand pCommand, NotificationListenerDelegate pListenerDelegate) {
        mBean = pCommand.getObjectName();
        filter = pCommand.getFilter();
        handback = pCommand.getHandback();
        config = pCommand.getConfig();
        delegate = pListenerDelegate;
        client = pCommand.getClient();
        handle = pHandle;
    }

    public void ping() {
        delegate.refresh(client);
    }

    public String getClient() {
        return client;
    }

    public String getHandle() {
        return handle;
    }

    public ObjectName getMBean() {
        return mBean;
    }

    public List<String> getFilter() {
        return filter;
    }

    public Object getHandback() {
        return handback;
    }

    public Map<String, ?> getConfig() {
        return config;
    }
}
