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
import org.jolokia.service.JolokiaContext;

/**
 * The notification backend managed is responsible for looking up
 * backends and managing their lifecycles.
 *
 * @author roland
 * @since 22.03.13
 */
class NotificationBackendManager {

    // Map mode to Backend and configs
    private final Map<String, NotificationBackend> backendMap = new HashMap<String, NotificationBackend>();
    private final Map<String, Map<String, ?>> backendConfigMap = new HashMap<String, Map<String, ?>>();
    private final JolokiaContext context;

    /**
     * Lookup backends and remember
     *
     * @param pContext jolokia context
     */
    NotificationBackendManager(JolokiaContext pContext) {
        context = pContext;
        PullNotificationBackend backend = new PullNotificationBackend(context);
        backendMap.put(backend.getType(), backend);
        backendConfigMap.put(backend.getType(),backend.getConfig());
    }

    /**
     * Lifecycle method for notifying the backends that the agent goes down
     *
     * @throws JMException
     */
    void destroy() throws JMException {
        for (NotificationBackend backend : backendMap.values()) {
            backend.destroy();
        }
    }

    /**
     * Get the global configuration from all registered backends. This
     * information is returned to the client when he registers.
     *
     * @return map with backend types as keys and their configuration as values (which
     *         is probably also a map)
     */
    Map<String, ?> getBackendConfig() {
        return Collections.unmodifiableMap(backendConfigMap);
    }

    /**
     * Lookup backend from the pre generated map of backends
     *
     * @param pType backend type to lookup
     */
    NotificationBackend getBackend(String pType) {
        NotificationBackend backend = backendMap.get(pType);
        if (backend == null) {
            throw new IllegalArgumentException("No backend of type '" + pType + "' registered");
        }
        return backend;
    }

    /**
     * Unsubscribe a notification from a backend
     *
     * @param pType backend type
     * @param pClient client id
     * @param pHandle notification handle
     */
    void unsubscribe(String pType, String pClient, String pHandle) {
        NotificationBackend backend = getBackend(pType);
        backend.unsubscribe(pClient,pHandle);
    }

    /**
     * Unregister a client completely. Every backend which holds
     * a notification for this client will get notified.
     *
     * @param pClient client to unregister.
     */
    void unregister(Client pClient) {
        for (String mode : pClient.getUsedBackendModes()) {
            NotificationBackend backend = getBackend(mode);
            backend.unregister(pClient.getId());
        }
    }
}
