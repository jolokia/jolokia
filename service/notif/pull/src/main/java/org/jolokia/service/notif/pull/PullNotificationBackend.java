package org.jolokia.service.notif.pull;

import java.util.Map;

import javax.management.Notification;
import javax.management.ObjectName;

import org.jolokia.server.core.http.BackChannel;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.notification.*;
import org.json.JSONObject;

/**
 * Pull based implementation for notifications
 *
 * @author roland
 * @since 20.03.13
 */
public class PullNotificationBackend extends AbstractJolokiaService<NotificationBackend> implements NotificationBackend {

    public static final String OBJECT_NAME = "jolokia:type=NotificationStore";

    // Store for holding the notification
    private PullNotificationStore store;

    // maximal number of entries *per* notification subscription
    private final int maxEntries = 100;

    // name as the MBean has been registered
    private ObjectName objectName;

    /**
     * Create a pull notification backend which will register an MBean allowing
     * to pull received notification
     *
     * @param order of this notification backend
     */
    public PullNotificationBackend(int order) {
        super(NotificationBackend.class,order);
    }

    /** {@inheritDoc} */
    public synchronized void init(JolokiaContext pContext) {
        if (getJolokiaContext() == null) {
            super.init(pContext);
            // TODO: Get configuration parameter for maxEntries
            store = new PullNotificationStore(maxEntries);
            objectName = registerJolokiaMBean(OBJECT_NAME,store);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (getJolokiaContext() != null) {
            unregisterJolokiaMBean(objectName);
            super.destroy();
        }
    }

    /** {@inheritDoc} */
    public String getNotifType() {
        return "pull";
    }

    /** {@inheritDoc} */
    public BackendCallback subscribe(final NotificationSubscription pSubscription) {
        return new BackendCallback() {
            /** {@inheritDoc} */
            public void handleNotification(Notification notification, Object handback) {
                store.add(pSubscription,notification);
            }
        };
    }

    /** {@inheritDoc} */
    public void channelInit(Client client, BackChannel channel) {
        // ignored here since this backend doesnt use back channels.
    }

    /** {@inheritDoc} */
    public void unsubscribe(String pClientId, String pHandle) {
        store.removeSubscription(pClientId, pHandle);
    }

    /** {@inheritDoc}
     * @param pClient*/
    public void unregister(Client pClient) {
        store.removeClient(pClient.getId());
    }

    /** {@inheritDoc} */
    public Map<String, ?> getConfig() {
        JSONObject ret = new JSONObject();
        ret.put("store",objectName.toString());
        ret.put("maxEntries",maxEntries);
        return ret.toMap();
    }
}
