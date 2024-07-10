package org.jolokia.server.core.service.notification;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.jolokia.server.core.http.BackChannel;
import org.jolokia.json.JSONObject;

/**
 * A Client holds all listener registration for a specific client. Also, it knows
 * how to create handles for new listener registrations and remembers the last ping of
 * a client.
 *
 * @author roland
 * @since 18.03.13
 */
public class Client {

    // Client ID
    private final String id;

    // Back channel which can be used an backend to transport notifications
    final private Map<String,BackChannel> backChannelMap;

    // Map of all registrations for a client
    final private Map<String, ListenerRegistration> listenerConfigMap;

    // Epoch time in millis since last refresh
    private long lastRefresh;

    // Used backend (content: Backend modes)
    private final Set<String> usedBackends;

    // Counter sequence
    private final AtomicLong handleSequence = new AtomicLong(0);

    /**
     * Initialize
     *
     * @param pId unique id for this client
     */
    public Client(String pId) {
        id = pId;
        listenerConfigMap = new HashMap<>();
        backChannelMap = new HashMap<>();
        usedBackends = new HashSet<>();
        lastRefresh = System.currentTimeMillis();
    }

    /**
     * Get a set of all known handles
     * @return handles
     */
    public Set<String> getHandles() {
        return listenerConfigMap.keySet();
    }

    /**
     * A a new listener registration to this config
     *
     * @param pRegistration registration to add
     * @param pHandle to add to
     */
    public void addNotification(String pHandle, ListenerRegistration pRegistration) {
        listenerConfigMap.put(pHandle,pRegistration);
    }

    /**
     * Increment handle and return it. This method must be used together with add() in
     * a synchronized blog.
     *
     * @return next handle
     */
    public String getNextHandle() {
        return Long.toString(handleSequence.incrementAndGet());
    }

    /**
     * Get a registration object for a given handle. If this handle
     * is not known, an exception is thrown.
     *
     * @param pHandle handle to lookup
     * @return registration object
     */
    public ListenerRegistration get(String pHandle) {
        ListenerRegistration config = listenerConfigMap.get(pHandle);
        if (config == null) {
            throw new IllegalArgumentException("No listener with handle " + pHandle + " created");
        }
        return config;
    }

    /**
     * Remove a handle. If now known, this will be silently ignored.
     *
     * @param pHandle handle to remove.
     */
    public void remove(String pHandle) {
        listenerConfigMap.remove(pHandle);
    }

    /**
     * Refresh this configuration by setting the last refresh time
     * to the current time.
     *
     */
    public void refresh() {
        lastRefresh = System.currentTimeMillis();
    }

    /**
     * Print out a JSON representation of all registered listeners. The key are the handles, the
     * value is the corresponding registration.
     *
     * @return JSON object for this configuration.
     */
    @SuppressWarnings("unchecked")
    public JSONObject list() {
        JSONObject ret = new JSONObject();
        for (Map.Entry<String,ListenerRegistration> entry : listenerConfigMap.entrySet()) {
            ret.put(entry.getKey(),entry.getValue().toJson());
        }
        return ret;
    }

    /**
     * Get the last refresh time of this configuration
     *
     * @return last refresh (epoch millis)
     */
    public long getLastRefresh() {
        return lastRefresh;
    }

    /**
     * UUID of this client
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Return the HTTP back channel or <code>null</code> if none is set.
     *
     * @param pMode for which backend a channel is required
     * @return back channel
     */
    public BackChannel getBackChannel(String pMode) {
        return backChannelMap.get(pMode);
    }

    /**
     * Set a back channel from the outside
     *
     * @param pMode backend mode
     * @param pChannel back channel to use
     */
    public void setBackChannel(String pMode, BackChannel pChannel) {
        backChannelMap.put(pMode,pChannel);
    }

    /**
     * Add a backend which is used by this client
     * @param pType backend type
     */
    public void addUsedBackend(String pType) {
        usedBackends.add(pType);
    }

    /**
     * Get all used backend types
     * @return used backend types
     */
    Set<String> getUsedBackendModes() {
        return usedBackends;
    }
}
