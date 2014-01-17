package org.jolokia.agent.service.jmx.handler.notification;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONObject;

/**
 * A Client holds all listener registration for a specific client. Also, it knows
 * how to create handles for new listener registrations and remembers the last ping of
 * a client.
 *
 * @author roland
 * @since 18.03.13
 */
class Client {

    // Client ID
    private String id;

    // Map of all registrations for a client
    private Map<String, ListenerRegistration> listenerConfigMap;

    // Epoch time in millis since last refresh
    private long lastRefresh;

    // Used backend (content: Backend modes)
    private Set<String> usedBackends;

    // Counter sequence
    private AtomicLong handleSequence = new AtomicLong(0);

    /**
     * Initialize
     */
    Client(String pId) {
        id = pId;
        listenerConfigMap = new HashMap<String, ListenerRegistration>();
        usedBackends = new HashSet<String>();
        lastRefresh = System.currentTimeMillis();
    }

    /**
     * Get a set of all known handles
     * @return handles
     */
     Set<String> getHandles() {
        return listenerConfigMap.keySet();
    }

    /**
     * A a new listener registration to this config
     *
     * @param pRegistration registration to add
     * @param pHandle to add to
     */
    void addNotification(String pHandle, ListenerRegistration pRegistration) {
        listenerConfigMap.put(pHandle,pRegistration);
    }

    /**
     * Increment handle and return it. This method must be used together with add() in
     * a synchronized blog.
     *
     * @return next handle
     */
    String getNextHandle() {
        return Long.toString(handleSequence.incrementAndGet());
    }

    /**
     * Get a registration object for a given handle. If this handle
     * is not known, an exception is thrown.
     *
     * @param pHandle handle to lookup
     * @return registration object
     */
     ListenerRegistration get(String pHandle) {
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
     void remove(String pHandle) {
        listenerConfigMap.remove(pHandle);
    }

    /**
     * Refresh this configuration by setting the last refresh time
     * to the current time.
     *
     */
    void refresh() {
        lastRefresh = System.currentTimeMillis();
    }

    /**
     * Print out a JSON representation of all registered listeners. The key are the handles, the
     * value is the corresponding registration.
     *
     * @return JSON object for this configuration.
     */
    JSONObject list() {
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
    String getId() {
        return id;
    }

    /**
     * Add a backend which is used by this client
     * @param pType backend type
     */
    void addUsedBackend(String pType) {
        usedBackends.add(pType);
    }

    /**
     * Get all used backend types
     * @return used backedn types
     */
    Set<String> getUsedBackendModes() {
        return usedBackends;
    }

}
