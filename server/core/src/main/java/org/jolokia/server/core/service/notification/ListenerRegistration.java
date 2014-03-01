package org.jolokia.server.core.service.notification;

import java.util.List;
import java.util.Map;

import javax.management.*;

import org.jolokia.server.core.request.notification.AddCommand;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A registration configuration for a specific listener. This includes a callback which is used
 * for dispatching notification to the backend.
 *
 * @author roland
 * @since 18.03.13
 */
public class ListenerRegistration {

    // the callback to be called when notifications come in
    private final BackendCallback callback;

    // the backend used for this listener
    private final String backendMode;

    // An optional filter extracted from the configuration
    private final NotificationFilterSupport filter;

    // Name of the MBean to register to
    private final ObjectName mbeanName;

    // optional handback returned to a client when a notification arrives
    private final Object handback;

    // extra backend configuration
    private final Map<String, ?> config;

    /**
     * Create a new configuration object for an addListener() request
     *
     * @param pCommand command from where to extract parameters
     * @param pCallback callback to call when a notification arrives
     */
    public ListenerRegistration(AddCommand pCommand, BackendCallback pCallback) {
        callback = pCallback;
        mbeanName = pCommand.getObjectName();
        handback = pCommand.getHandback();
        config = pCommand.getConfig();
        filter = createFilter(pCommand.getFilter());
        backendMode = pCommand.getMode();
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
        if (config != null) {
            ret.put("config",config);
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

    /** Extra backend configuration */
    Map<String, ?> getConfig() {
        return config;
    }

    /** Backend used */
    public String getBackendMode() {
        return backendMode;
    }

    // ====================================================================================
    // Filters are always on the notification type, but there can be multiple given, which are ORed together
    private NotificationFilterSupport createFilter(List<String> pFilters) {
        if (pFilters != null) {
            NotificationFilterSupport filterSupport = new NotificationFilterSupport();
            for (String f :  pFilters) {
                filterSupport.enableType(f);
            }
            return filterSupport;
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
