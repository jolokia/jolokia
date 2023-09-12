/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.server.core.request.notification;

import java.util.*;

import javax.management.*;

import org.jolokia.server.core.util.EscapeUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Command for adding a notification listener for a client with optional
 * filter and handback.
 *
 * @author roland
 * @since 19.03.13
 */
public class AddCommand extends ClientCommand {

    // MBean on which to register a notification listener
    private final ObjectName objectName;

    // Backend mode
    private final String mode;

    // Extra configuration specific for the backend
    private Map<String, Object> config;

    // List of filter on notification types which are ORed together
    private List<String> filter;

    // An arbitrary handback returned for every notification received
    private Object handback;

    /**
     * Add for GET requests, which mus have the path part '/client/mode/mbean'.
     * Optionally an '/filter1,filter2/config/handback' part can be provided.
     * (an handback works only with filters given)
     *
     * To add an empty filter (so that the following parameters can be used, too), use a space
     * for this part (%20). To add an empty config use "{}". But at the end,
     * you are better off by using the POST variant anyways for adding listeners.
     *
     * @param pStack path stack from where to extract the information
     * @throws MalformedObjectNameException if the given mbean name is not a valid {@link ObjectName}
     */
    AddCommand(Stack<String> pStack) throws MalformedObjectNameException {
        super(NotificationCommandType.ADD, pStack);
        if (pStack.isEmpty()) {
            throw new IllegalArgumentException("No mode give for " + NotificationCommandType.ADD);
        }
        mode = pStack.pop();
        if (pStack.isEmpty()) {
            throw new IllegalArgumentException("No MBean name given for " + NotificationCommandType.ADD);
        }
        objectName = new ObjectName(pStack.pop());
        if (!pStack.isEmpty()) {
            String element = pStack.pop();
            if (!element.trim().isEmpty()) {
                filter = EscapeUtil.split(element, EscapeUtil.CSV_ESCAPE, ",");
            }
        }
        if (!pStack.isEmpty()) {
            config = parseConfig(pStack.pop());
        }
        if (!pStack.isEmpty()) {
            handback = pStack.pop();
        }
    }

    /**
     * For POST requests, the key 'client','mode' and 'mbean' must be given in the request payload.
     * Optionally, a 'filter' element with an array of string filters (or a single filter as string)
     * can be given. This filter gets applied for the notification type (see {@link NotificationFilterSupport})
     *
     * @param pMap request map
     * @throws MalformedObjectNameException if the given mbean name is not a valid {@link ObjectName}
     */
    AddCommand(Map<String,?> pMap) throws MalformedObjectNameException {
        super(NotificationCommandType.ADD, pMap);
        if (!pMap.containsKey("mode")) {
            throw new IllegalArgumentException("No mode give for " + NotificationCommandType.ADD);
        }
        mode = (String) pMap.get("mode");
        if (!pMap.containsKey("mbean")) {
            throw new IllegalArgumentException("No MBean name given for " + NotificationCommandType.ADD);
        }
        objectName = new ObjectName((String) pMap.get("mbean"));
        Object f = pMap.get("filter");
        if (f != null) {
            filter = f instanceof List ? (List<String>) f : Collections.singletonList(f.toString());
        }
        Object c = pMap.get("config");
        if (c != null) {
            config = c instanceof Map ? (Map<String, Object>) c : parseConfig(c.toString());
        }
        handback = pMap.get("handback");
    }

    /**
     * The backend mode specifies which backend is used for delivering a notification.
     * E.g. "pull" will store notification server side which must be fetched actively
     * by a client
     * @return backend mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * Objectname of the MBean the listener should connect to
     * @return mbean name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    /**
     * A list of string filters or <code>null</code> if no
     * filters has been provided
     * @return list of filters or null if none is given.
     */
    public List<String> getFilter() {
        return filter;
    }

    /**
     * A handback object. For GET requests this is a String, for POSTS it can be
     * an arbitrary JSON structure.
     *
     * @return handback object or null if none has been provided
     */
    public Object getHandback() {
        return handback;
    }

    /**
     * Get the configuration for an add request
     * @return map holding extra configuration. This can be null or empty.
     */
    public Map<String, Object> getConfig() {
        return config;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("mbean",objectName.toString());
        ret.put("mode",mode);
        if (filter != null && filter.size() > 0) {
            ret.put("filter",filter);
        }
        if (config != null && config.size() > 0) {
            ret.put("config",config);
        }
        if (handback != null) {
            ret.put("handback",handback);
        }
        return ret;
    }

    // ==============================================================================================

    // Parse a string as configuration object
    private Map<String, Object> parseConfig(String pElement) {
        try {
            return (Map<String, Object>) new JSONParser().parse(pElement);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Cannot parse config '" + pElement + "' as JSON Object",e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Cannot parse config '" + pElement + "' as JSON Object",e);
        }
    }
}
