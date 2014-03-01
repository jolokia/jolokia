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

package org.jolokia.service.pullnotif;

import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.jolokia.server.core.service.notification.NotificationSubscription;

/**
* @author roland
* @since 23.03.13
*/
class TestNotificationSubscription implements NotificationSubscription {

    private String handle;
    private String client;
    private Object handback;

    TestNotificationSubscription() {
    }

    public TestNotificationSubscription(String pHandle) {
        handle = pHandle;
    }

    public TestNotificationSubscription(String pClient, String pHandle) {
        handle = pHandle;
        client = pClient;
    }

    public TestNotificationSubscription(String pHandle, String pClient, Object pHandback) {
        handle = pHandle;
        client = pClient;
        handback = pHandback;
    }

    public void ping() {
    }

    public String getClient() {
        return client;
    }

    public String getHandle() {
        return handle;
    }

    public ObjectName getMBean() {
        return null;
    }

    public List<String> getFilter() {
        return null;
    }

    public Object getHandback() {
        return handback;
    }

    public Map<String, ?> getConfig() {
        return null;
    }
}
