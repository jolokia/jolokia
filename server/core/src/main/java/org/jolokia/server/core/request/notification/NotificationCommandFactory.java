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

import javax.management.MalformedObjectNameException;

/**
 * This factory produces {@link NotificationCommand} objects which specify a certain aspect
 * during notification listener registration. I.e. when a request type is
 * <code>NOTIFICATION</code> then there are several commands like 'register', 'add', 'list'
 * or 'ping' responsible for the correct listener management.
 *
 * @author roland
 * @since 19.03.13
 */
public final class NotificationCommandFactory {

    // Only static methods
    private NotificationCommandFactory() {}

    private static final Map<NotificationCommandType, Creator> CREATORS = new HashMap<>();

    /**
     * Create a command out of the request path. The command type must be on top
     * of the stack with the command specific options following. This method is suitable for
     * parsing GET requests.
     *
     * @param pStack string stack with various path parts specifying the command detail.
     * @return the created command
     * @throws MalformedObjectNameException if an objectname part has an invalid format
     */
    public static NotificationCommand createCommand(Deque<String> pStack) throws MalformedObjectNameException {
        String command = pStack.pop();
        NotificationCommandType type = NotificationCommandType.getTypeByName(command);
        return CREATORS.get(type).create(pStack, null);
    }

    /**
     * Create a command out of a map, possible out of a {@link org.json.simple.JSONObject}.
     * The command name itself must be given under the key "command", the rest of the map
     * holds the specifics for this command.
     *
     * @param pCommandMap parameter from which to extract the command
     * @return the created command
     * @throws MalformedObjectNameException if an objectname part has an invalid format
     */
    public static NotificationCommand createCommand(Map<String, ?> pCommandMap) throws MalformedObjectNameException {
        String command = (String) pCommandMap.get("command");
        NotificationCommandType type = NotificationCommandType.getTypeByName(command);
        return CREATORS.get(type).create(null,pCommandMap);
    }

    // =====================================================================

    // Helper interface for the various specific command command creation stuff. Could have
    // hold 2 methods but for simplicity reason (and because it is private) its a single one
    // where only one of the parameters is filled when called.
    private interface Creator {
        /**
         * Create the command either from the given stack (checked first) or a given map
         */
        NotificationCommand create(Deque<String> pStack,Map<String,?> pMap) throws MalformedObjectNameException;
    }

    // Build up the lookup map
    static {
        CREATORS.put(NotificationCommandType.REGISTER, (pStack, pMap) -> new RegisterCommand());
        CREATORS.put(NotificationCommandType.UNREGISTER, (pStack, pMap) -> pStack != null ? new UnregisterCommand(pStack) : new UnregisterCommand(pMap));
        CREATORS.put(NotificationCommandType.ADD, (pStack, pMap) -> pStack != null ? new AddCommand(pStack) : new AddCommand(pMap));
        CREATORS.put(NotificationCommandType.REMOVE, (pStack, pMap) -> pStack != null ? new RemoveCommand(pStack) : new RemoveCommand(pMap));
        CREATORS.put(NotificationCommandType.PING, (pStack, pMap) -> pStack != null ? new PingCommand(pStack) : new PingCommand(pMap));
        CREATORS.put(NotificationCommandType.OPEN, (pStack, pMap) -> pStack != null ? new OpenCommand(pStack) : new OpenCommand(pMap));
        CREATORS.put(NotificationCommandType.LIST, (pStack, pMap) -> pStack != null ? new ListCommand(pStack) : new ListCommand(pMap));


    }

}
