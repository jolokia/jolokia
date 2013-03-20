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

package org.jolokia.request.notification;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum specifying the possible notification command types
 *
 * @author roland
 * @since 19.03.13
 */
public enum NotificationCommandType {

    /**
     * Register a new client
     */
    REGISTER("register"),
    /**
     * Unregister a client and remove all listeners
     */
    UNREGISTER("unregister"),
    /**
     * Add a new listener for a certain client
     */
    ADD("add"),
    /**
     * Remove a listener for a certain client
     */
    REMOVE("remove"),
    /**
     * Ping to update the freshness of a client
     */
    PING("ping"),
    /**
     * List all notifications for a client
     */
    LIST("list");

    // type as given in the request
    private String type;

    // lookup for name-to-type
    private static final Map<String, NotificationCommandType> COMMANDS_BY_NAME =
            new HashMap<String, NotificationCommandType>();

    // Initialise lookup map
    static {
        for (NotificationCommandType t : NotificationCommandType.values()) {
            COMMANDS_BY_NAME.put(t.getType(), t);
        }
    }

    private NotificationCommandType(String pPType) {
        type = pPType;
    }

    /**
     * Return the name of the type
     * @return name of type
     */
    public String getType() {
        return type;
    }

    /**
     * Case insensitive lookup by name
     *
     * @param pName name of the command (lower- or upper case)
     * @return command looked up
     * @throws IllegalArgumentException if the argument is either <code>null</code> or
     *         does not map to a type.
     */
    public static NotificationCommandType getTypeByName(String pName) {
        if (pName == null) {
            throw new IllegalArgumentException("No command given");
        }
        NotificationCommandType command = COMMANDS_BY_NAME.get(pName.toLowerCase());
        if (command == null) {
            throw new UnsupportedOperationException("No command with name '" + pName + "' exists");
        }
        return command;
    }
}
