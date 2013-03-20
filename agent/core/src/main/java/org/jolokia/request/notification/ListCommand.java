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

/**
 * List command for getting all registered notification for a client.
 * Only the client id is required.
 *
 * @author roland
 * @since 19.03.13
 */
public class ListCommand extends ClientCommand {

    /**
     * Constructor for GET requests
     * @param pStack path stack
     */
    ListCommand(Stack<String> pStack) {
        super(NotificationCommandType.LIST, pStack);
    }

    /**
     * Constructor for POST requests
     * @param pMap request map
     */
    ListCommand(Map<String, ?> pMap) {
        super(NotificationCommandType.LIST, pMap);
    }
}
