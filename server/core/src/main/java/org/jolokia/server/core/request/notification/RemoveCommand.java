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

import java.util.Map;
import java.util.Stack;

import org.json.simple.JSONObject;

/**
 * Remove a listener by its handle
 *
 * @author roland
 * @since 19.03.13
 */
public class RemoveCommand extends ClientCommand {

    private String handle;

    /**
     * Remove a listener for GET requests. The handle must be given (after the client path part),
     * otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param pStack path stack
     */
    RemoveCommand(Stack<String> pStack) {
        super(NotificationCommandType.REMOVE, pStack);
        if (pStack.isEmpty()) {
            throw new IllegalArgumentException("No notification handle given for " + NotificationCommandType.REMOVE);
        }
        handle = pStack.pop();
    }

    /**
     * Remove a listener for POST requests. The map must contain a key "handle" for specifying the
     * notification registration to be removed.
     *
     * @param pMap request map
     */
    RemoveCommand(Map<String, ?> pMap) {
        super(NotificationCommandType.REMOVE, pMap);
        handle = (String) pMap.get("handle");
        if (handle == null) {
            throw new IllegalArgumentException("No notification handle given for " + NotificationCommandType.REMOVE);
        }
    }

    public String getHandle() {
        return handle;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("handle",handle);
        return ret;
    }
}
