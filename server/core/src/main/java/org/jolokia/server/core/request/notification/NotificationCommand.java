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

import org.jolokia.json.JSONObject;

/**
 * Base command class holding the command type
 *
 * @author roland
 * @since 19.03.13
 */
public abstract class NotificationCommand {

    // Command action
    private final NotificationCommandType type;

    /**
     * Constructor with type
     *
     * @param pType type of this command
     */
    protected NotificationCommand(NotificationCommandType pType) {
        type = pType;
    }

    /**
     * Get the type of this command
     *
     * @return command type
     */
    public NotificationCommandType getType() {
        return type;
    }

    /** {@inheritDoc} */
    public JSONObject toJSON() {
        JSONObject ret = new JSONObject();
        ret.put("command",type.getType());
        return ret;
    }
}
