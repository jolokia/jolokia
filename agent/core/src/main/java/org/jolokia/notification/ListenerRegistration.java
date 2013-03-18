package org.jolokia.notification;

import java.util.List;

import javax.management.*;

import org.json.simple.JSONObject;

/**
 * A registration configuration for a specific listener. This includes a callback which is used
 * for dispatching notification to the backend.
 *
 * @author roland
 * @since 18.03.13
 */
class ListenerRegistration {

    // the callback to be called when notifications come in
    private BackendCallback callback;

    // An optional filter extracted from the configuration
    private NotificationFilter filter;

    // Name of the MBean to register to
    private ObjectName mbeanName;

    // The overall configuration, used for adding this config to a list
    private JSONObject config;

    /**
     * Create a new configuration object for an addListener() request
     *
     * @param pMBeanName name of the MBean to register for
     * @param pConfig listener configuration
     * @param pCallback callback to call when a notification arrives
     */
    ListenerRegistration(ObjectName pMBeanName, JSONObject pConfig, BackendCallback pCallback) {
        callback = pCallback;
        mbeanName = pMBeanName;
        config = pConfig;
        if (pConfig.containsKey("filter")) {
            Object filters = pConfig.get("filter");
            filter = createFilter(filters);
        } else {
            filter = null;
        }
    }

    /**
     * Return a JSON representation of this config (used for list)
     * @return JSON representation
     */
    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.putAll(config);
        ret.put("mbean", mbeanName.toString());
        return ret;
    }

    /** Get callback */
    public BackendCallback getCallback() {
        return callback;
    }

    /** Get Filter */
    public NotificationFilter getFilter() {
        return filter;
    }

    /** Get Objectname */
    public ObjectName getMBeanName() {
        return mbeanName;
    }

    /** Get the handback used for the JMX listener */
    public Object getHandback() {
        return config.get("handback");
    }

    // ====================================================================================
    // Filters are always on the notification type, but there can be multiple given, which are ORed together
    private NotificationFilter createFilter(Object pFilters) {
        NotificationFilterSupport filter = new NotificationFilterSupport();
        if (pFilters instanceof List) {
            for (Object f : (List) pFilters) {
                if (f instanceof String) {
                    filter.enableType((String) f);
                } else throw new IllegalArgumentException("Not a valid type filter: " + f);
            }
        } else if (pFilters instanceof String) {
            filter.enableType((String) pFilters);
        } else {
            throw new IllegalArgumentException("Not a valid type filter: " + pFilters);
        }
        return filter;
    }
}
