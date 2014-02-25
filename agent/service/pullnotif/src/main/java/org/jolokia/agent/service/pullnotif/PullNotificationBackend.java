package org.jolokia.agent.service.pullnotif;

import java.util.Map;

import javax.management.*;

import org.jolokia.core.service.AbstractJolokiaService;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.service.notification.*;
import org.jolokia.core.util.jmx.JmxUtil;
import org.json.simple.JSONObject;

/**
 * Dummy implementation
 *
 * @author roland
 * @since 20.03.13
 */
public class PullNotificationBackend extends AbstractJolokiaService<NotificationBackend> implements NotificationBackend {

    // Store for holding the notification
    private PullNotificationStore store;

    // maximal number of entries *per* notification subscription
    private int maxEntries = 100;

    // MBean name of this stored
    private ObjectName mbeanName;

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
        String jolokiaId = pContext.getAgentDetails().getAgentId();
        // TODO: Get configuration parameter for maxEntries
        store = new PullNotificationStore(maxEntries);
        mbeanName = JmxUtil.newObjectName("jolokia:type=NotificationStore,agent=" + jolokiaId);
        try {
            pContext.registerMBean(store, mbeanName.getCanonicalName());
        } catch (JMException e) {
            throw new IllegalArgumentException("Cannot register MBean " + mbeanName + " as notification pull store: " + e,e);
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
        ret.put("store",mbeanName.toString());
        ret.put("maxEntries",maxEntries);
        return ret;
    }
}
