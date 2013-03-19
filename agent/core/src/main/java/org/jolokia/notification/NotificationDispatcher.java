package org.jolokia.notification;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.json.simple.JSONObject;

/**
 * @author roland
 * @since 18.03.13
 */
public class NotificationDispatcher {

    private final NotificationBackend[] BACKENDS = new NotificationBackend[]{

    };

    private final Map<String, NotificationBackend> backendMap = new HashMap<String, NotificationBackend>();

    private NotificationListenerDelegate listenerDelegate;

    public NotificationDispatcher(MBeanServerExecutor executor) {
        for (NotificationBackend backend : BACKENDS) {
            backendMap.put(backend.getType(), backend);
        }
        listenerDelegate = new NotificationListenerDelegate(executor);
    }

    // Create unique client identifier
    public String register() {
        return listenerDelegate.register();
    }

    public void unregister(String client) throws MBeanException, IOException, ReflectionException {
        listenerDelegate.unregister(client);
    }

    /**
     * Add a new notification listener for a given client and MBean
     * @param client client id identifying the current client. This id must be generated on
     *               the client side, butshould be hidden away by a client library. Needed
     *               for associating all listeners with a certain client.
     *               Maybe security should be added here too ?
     * @param mbean the MBean on which to register the listener
     * @param backendType specifying the notification backend/model. Something like "pull", "sockjs",
     *             "ws" or "push"
     * @param config configuration specific for the backend but also including common options
     *               like filters or handback objects.
     * @return a map containing the handler id and the freshness interval (i.e. how often ping must be called before
     *         the listener is considered to be stale.
     */
    public String addListener(String client, ObjectName mbean, String backendType, JSONObject config) throws MBeanException, IOException, ReflectionException {
        NotificationBackend backend = getBackend(backendType);
        return listenerDelegate.addListener(client,createListenerConfig(mbean, backend, config));
    }

    /**
     * Remove a notification listener
     * @param client client id (see above)
     * @param handle handle created during addListener()
     */
    public void removeListener(String client, String handle) throws MBeanException, IOException, ReflectionException {
        listenerDelegate.removeListener(client, handle);
    }

    /**
     * Updated freshness of this client. Since we use a stateless model, the server
     * needs to know somehow, when a client fades away without unregistering all its
     * listeners. The idea is, that a client have to send a ping within certain
     * intervals and if this ping is missing, the client is considered as stale and
     * all its listeners get removed automatically then.
     *
     * @param client client id (see above)
     */
    public void ping(String client) {
        listenerDelegate.refresh(client);
    }

    /**
     * List all listener registered by a client along with its configuration parameters
     *
     * @param client client id (see above)
     * @return a JSON object describing all listeners. Keys are probably the handles
     *         created during addListener(). Values are the configuration of the
     *         listener jobs.
     */
    public JSONObject list(String client) {
        return listenerDelegate.list(client);
    }

    // =====================================================================================================

    private ListenerRegistration createListenerConfig(ObjectName pMbean, NotificationBackend pBackend, JSONObject pConfig) {
        BackendCallback callback = pBackend.getBackendCallback(pConfig);
        return new ListenerRegistration(pMbean,pConfig,callback);
    }

    // Lookup backend from the pre generated map of backends
    private NotificationBackend getBackend(String type) {
        NotificationBackend backend = backendMap.get(type);
        if (backend == null) {
            throw new IllegalArgumentException("No backend of type '" + type + "' registered");
        }
        return backend;
    }
}
