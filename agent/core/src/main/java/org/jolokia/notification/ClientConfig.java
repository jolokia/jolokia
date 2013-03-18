package org.jolokia.notification;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONObject;

/**
 * A ClientConfig holds all listener registration for a specific client. Also, it knows
 * how to create handles for new listener registrations and remembers the last ping of
 * a client.
 *
 * @author roland
 * @since 18.03.13
 */
public class ClientConfig {

    // Map of all registrations for a client
    private Map<String, ListenerRegistration> listenerConfigMap;

    // Epoch time in millis since last refresh
    private long lastRefresh;

    // Counter sequence
    private AtomicLong handleSequence = new AtomicLong(0);

    /**
     * Initialize
     */
    public ClientConfig() {
        listenerConfigMap = new HashMap<String, ListenerRegistration>();
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
     * @return the newly created handle.
     */
    public String add(ListenerRegistration pRegistration) {
        String handle = Long.toString(handleSequence.incrementAndGet());
        listenerConfigMap.put(handle,pRegistration);
        return handle;
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

}
