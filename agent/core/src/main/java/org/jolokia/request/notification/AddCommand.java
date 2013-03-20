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

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.util.EscapeUtil;

/**
 * @author roland
 * @since 19.03.13
 */
public class AddCommand extends ClientCommand {

    private final ObjectName objectName;

    private List<String> filter;

    private Object handback;

    AddCommand(Stack<String> pStack) throws MalformedObjectNameException {
        super(CommandType.ADD, pStack);
        if (pStack.isEmpty()) {
            throw new IllegalArgumentException("No MBean name given for " + CommandType.ADD);
        }
        objectName = new ObjectName(pStack.pop());
        if (!pStack.isEmpty()) {
            filter = EscapeUtil.split(pStack.pop(),EscapeUtil.CSV_ESCAPE,",");
        }
        if (!pStack.isEmpty()) {
            handback = pStack.pop();
        }
    }

    AddCommand(Map<String,?> pMap) throws MalformedObjectNameException {
        super(CommandType.ADD, pMap);
        if (!pMap.containsKey("mbean")) {
            throw new IllegalArgumentException("No MBean name given for " + CommandType.ADD);
        }
        objectName = new ObjectName((String) pMap.get("mbean"));
        Object f = pMap.get("filter");
        if (f != null) {
            filter = f instanceof List ? (List<String>) f : Arrays.asList(f.toString());
        }
        handback = pMap.get("handback");
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public List<String> getFilter() {
        return filter;
    }

    public Object getHandback() {
        return handback;
    }
}
