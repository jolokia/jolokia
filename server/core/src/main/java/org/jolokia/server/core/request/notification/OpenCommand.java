package org.jolokia.server.core.request.notification;/*
 *
 * Copyright 2015 Roland Huss
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

import java.util.Deque;
import java.util.Map;

import org.jolokia.server.core.request.BadRequestException;

/**
 * Command for creating and opening a channel for transmitting notifications
 * back to the client.
 *
 * A <code>mode</code> must be given to detect the proper backend.
 *
 * @author roland
 * @since 19/10/15
 */
public class OpenCommand extends ClientCommand {

    private final String mode;

    /**
     * Constructor for GET requests
     * @param pStack path parameters
     */
    public OpenCommand(Deque<String> pStack) throws BadRequestException {
        super(NotificationCommandType.OPEN, pStack);
        if (pStack.isEmpty()) {
            throw new BadRequestException("No mode give for " + NotificationCommandType.OPEN);
        }
        mode = pStack.pop();
    }

    /**
     * Constructor for POST requests
     *
     * @param pMap map containing parameters
     */
    public OpenCommand(Map<String, ?> pMap) throws BadRequestException {
        super(NotificationCommandType.OPEN, pMap);
                if (!pMap.containsKey("mode")) {
            throw new BadRequestException("No mode give for " + NotificationCommandType.ADD);
        }
        mode = (String) pMap.get("mode");
    }

    public String getMode() {
        return mode;
    }

}
