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

import java.util.Deque;
import java.util.Map;

/**
 * Unregister a client
 *
 * @author roland
 * @since 19.03.13
 */
public class UnregisterCommand extends ClientCommand {

    /**
     * Constructor for GET requests
     * @param pStack path stack
     */
    UnregisterCommand(Deque<String> pStack) {
        super(NotificationCommandType.UNREGISTER, pStack);
    }

    /**
     * Constructor for POST requests
     * @param pMap request map
     */
    UnregisterCommand(Map<String,?> pMap) {
        super(NotificationCommandType.UNREGISTER,pMap);
    }
}
