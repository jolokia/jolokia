/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.service.jmx;

import java.util.Deque;
import java.util.Map;
import javax.management.MBeanInfo;
import javax.management.ObjectName;

import org.jolokia.server.core.service.api.DataUpdater;

public class CustomUpdater extends DataUpdater {

    public CustomUpdater(int pOrderId) {
        super(pOrderId);
    }

    @Override
    public String getKey() {
        return "isSpecial";
    }

    @Override
    public void update(Map<String, Object> pMap, ObjectName pObjectName, MBeanInfo pMBeanInfo, Deque<String> pPathStack) {
        pMap.put(getKey(), "very special");
    }

}
