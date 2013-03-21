package org.jolokia.handler.notification;

import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.jolokia.notification.BackendRegistration;
import org.jolokia.request.notification.AddCommand;

/**
 * Class holding registration information
 *
 * @author roland
 * @since 21.03.13
 */
public class BackendRegistrationImpl implements BackendRegistration {

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
    private String client;

    /**
     * Registration used when registering at a notification backend
     */
    BackendRegistrationImpl(AddCommand pCommand, NotificationListenerDelegate pListenerDelegate) {
        mBean = pCommand.getObjectName();
        filter = pCommand.getFilter();
        handback = pCommand.getHandback();
        config = pCommand.getConfig();
        delegate = pListenerDelegate;
        client = pCommand.getClient();
    }

    public void ping() {
        delegate.refresh(client);
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
