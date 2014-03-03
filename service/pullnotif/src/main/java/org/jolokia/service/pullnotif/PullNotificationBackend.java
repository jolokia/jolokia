package org.jolokia.service.pullnotif;

import java.util.Map;

import javax.management.Notification;
import javax.management.ObjectName;

import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.notification.*;
import org.json.simple.JSONObject;

/**
 * Dummy implementation
 *
 * @author roland
 * @since 20.03.13
 */
public class PullNotificationBackend extends AbstractJolokiaService<NotificationBackend> implements NotificationBackend {

    public static final String OBJECT_NAME = "jolokia:type=NotificationStore";

    // Store for holding the notification
    private PullNotificationStore store;

    // maximal number of entries *per* notification subscription
    private int maxEntries = 100;

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
    public void init(JolokiaContext pContext) {
        super.init(pContext);
        // TODO: Get configuration parameter for maxEntries
        store = new PullNotificationStore(maxEntries);
        objectName = registerJolokiaMBean(OBJECT_NAME,store);
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        unregisterJolokiaMBean(objectName);
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
    public void unsubscribe(String pClientId, String pHandle) {
        store.removeSubscription(pClientId, pHandle);
    }

    /** {@inheritDoc} */
    public void unregister(String pClientId) {
        store.removeClient(pClientId);
    }

    /** {@inheritDoc} */
    public Map<String, ?> getConfig() {
        JSONObject ret = new JSONObject();
        ret.put("store",objectName.toString());
        ret.put("maxEntries",maxEntries);
        return ret;
    }
}
