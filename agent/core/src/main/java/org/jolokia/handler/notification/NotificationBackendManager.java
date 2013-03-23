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

package org.jolokia.handler.notification;

import java.util.*;

import javax.management.JMException;

import org.jolokia.detector.ServerHandle;
import org.jolokia.notification.NotificationBackend;
import org.jolokia.notification.pull.PullNotificationBackend;

/**
 * @author roland
 * @since 22.03.13
 */
public class NotificationBackendManager {

    // Map mode to Backend and configs
    private final Map<String, NotificationBackend> backendMap       = new HashMap<String, NotificationBackend>();
    private final Map<String, Map<String, ?>>      backendConfigMap = new HashMap<String, Map<String, ?>>();

    // Lookup backends and remember
    NotificationBackendManager(ServerHandle pServerHandle) {
        PullNotificationBackend backend = new PullNotificationBackend(pServerHandle.getJolokiaId());
        backendMap.put(backend.getType(), backend);
        backendConfigMap.put(backend.getType(),backend.getConfig());
    }

    public void destroy() throws JMException {
        for (NotificationBackend backend : backendMap.values()) {
            backend.destroy();
        }
    }

    public Map<String, ?> getBackendConfig() {
        return Collections.unmodifiableMap(backendConfigMap);
    }

    // Lookup backend from the pre generated map of backends
    NotificationBackend getBackend(String type) {
        NotificationBackend backend = backendMap.get(type);
        if (backend == null) {
            throw new IllegalArgumentException("No backend of type '" + type + "' registered");
        }
        return backend;
    }

    public void unsubscribe(String pBackendMode, String pClient, String pHandle) {
        NotificationBackend backend = getBackend(pBackendMode);
        backend.unsubscribe(pClient,pHandle);
    }

    public void unregister(Client pClient) {
        for (String mode : pClient.getUsedBackendModes()) {
            NotificationBackend backend = getBackend(mode);
            backend.unregister(pClient.getId());
        }
    }
}
