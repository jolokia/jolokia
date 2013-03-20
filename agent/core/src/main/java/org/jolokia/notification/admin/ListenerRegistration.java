package org.jolokia.notification.admin;

import java.util.List;

import javax.management.*;

import org.jolokia.notification.BackendCallback;
import org.json.simple.JSONArray;
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
    private final BackendCallback callback;

    // An optional filter extracted from the configuration
    private final NotificationFilterSupport filter;

    // Name of the MBean to register to
    private final ObjectName mbeanName;

    // optional handback returned to a client when a notification arrives
    private final Object handback;

    /**
     * Create a new configuration object for an addListener() request
     *
     * @param pMBeanName name of the MBean to register for
     * @param pFilters optional list of filters (might be null)
     * @param pHandback handback returned to the listeners
     * @param pCallback callback to call when a notification arrives
     */
    ListenerRegistration(ObjectName pMBeanName, List<String> pFilters, Object pHandback, BackendCallback pCallback) {
        callback = pCallback;
        mbeanName = pMBeanName;
        handback = pHandback;
        filter = createFilter(pFilters);
    }

    /**
     * Return a JSON representation of this config (used for list)
     * @return JSON representation
     */
    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("mbean", mbeanName.toString());
        if (filter != null) {
            ret.put("filter",filterToJSON(filter));
        }
        if (handback != null) {
            ret.put("handback",handback);
        }
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
        return handback;
    }

    // ====================================================================================
    // Filters are always on the notification type, but there can be multiple given, which are ORed together
    private NotificationFilterSupport createFilter(List<String> pFilters) {
        if (pFilters != null) {
            NotificationFilterSupport filter = new NotificationFilterSupport();
            for (String f :  pFilters) {
                filter.enableType(f);
            }
            return filter;
        } else {
            return null;
        }
    }

    private JSONArray filterToJSON(NotificationFilterSupport pFilter) {
        JSONArray ret = new JSONArray();
        for (String f : pFilter.getEnabledTypes()) {
            ret.add(f);
        }
        return ret;
    }
}
