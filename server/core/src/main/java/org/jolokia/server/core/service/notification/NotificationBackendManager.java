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

package org.jolokia.server.core.service.notification;

import java.util.HashMap;
import java.util.Map;

import org.jolokia.server.core.service.api.JolokiaContext;

/**
 * The notification backend managed is responsible for looking up
 * backends and managing their lifecycles.
 *
 * @author roland
 * @since 22.03.13
 */
public class NotificationBackendManager {

    // Map mode to Backend and configs
    private final JolokiaContext context;

    /**
     * Lookup backends and remember
     *
     * @param pContext jolokia context
     */
    public NotificationBackendManager(JolokiaContext pContext) {
        context = pContext;
    }

    /**
     * Get the global configuration from all registered backends. This
     * information is returned to the client when he registers.
     *
     * @return map with backend types as keys and their configuration as values (which
     *         is probably also a map)
     */
    public Map<String, ?> getBackendConfig() {
        Map<String, Map<String, ?>> configMap = new HashMap<String, Map<String, ?>>();

        for (NotificationBackend backend : context.getServices(NotificationBackend.class)) {
            configMap.put(backend.getNotifType(),backend.getConfig());
        }
        return configMap;
    }

    /**
     * Lookup backend from the pre generated map of backends
     *
     * @param pType backend type to lookup
     */
    public NotificationBackend getBackend(String pType) {
        for (NotificationBackend backend : context.getServices(NotificationBackend.class)) {
            if (backend.getNotifType().equalsIgnoreCase(pType)) {
                return backend;
            }
        }
        throw new IllegalArgumentException("No backend of type '" + pType + "' registered");
    }

    /**
     * Unsubscribe a notification from a backend
     *
     * @param pType backend type
     * @param pClient client id
     * @param pHandle notification handle
     */
    public void unsubscribe(String pType, String pClient, String pHandle) {
        NotificationBackend backend = getBackend(pType);
        backend.unsubscribe(pClient,pHandle);
    }

    /**
     * Unregister a client completely. Every backend which holds
     * a notification for this client will get notified.
     *
     * @param pClient client to unregister.
     */
    public void unregister(Client pClient) {
        for (String mode : pClient.getUsedBackendModes()) {
            NotificationBackend backend = getBackend(mode);
            backend.unregister(pClient.getId());
        }
    }
}
