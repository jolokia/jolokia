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

import java.util.Map;
import java.util.Stack;

import org.json.simple.JSONObject;

/**
 * A base command which should be subclassed by every command
 * requiring  'client' attribute.
 *
 * @author roland
 * @since 19.03.13
 */
public abstract class ClientCommand extends NotificationCommand {

    // Client which is typically a UUID
    private String client;

    /**
     * Constructor used for GET requests. If no client id is given
     * as first part of the path an {@link IllegalArgumentException}
     * is thrown.
     *
     * @param pType command type
     * @param pStack stack which on top must hold the client id
     */
    protected ClientCommand(NotificationCommandType pType, Stack<String> pStack) {
        super(pType);
        if (pStack.isEmpty()) {
            throw new IllegalArgumentException("No notification client given for '" + pType + "'");
        }
        client = pStack.pop();
    }

    /**
     * Constructor for POST requests. If no client is given under the
     * key "client" an {@link IllegalArgumentException} is thrown
     *
     * @param pType command type
     * @param pMap map holding the request
     */
    protected ClientCommand(NotificationCommandType pType, Map<String, ?> pMap) {
        super(pType);
        client = (String) pMap.get("client");
        if (client == null) {
            throw new IllegalArgumentException("No notification client given for '" + pType + "'");
        }
    }

    public String getClient() {
        return client;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("client",client);
        return ret;
    }
}
