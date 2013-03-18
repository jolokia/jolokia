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

package org.jolokia.notification;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.json.simple.JSONObject;

/**
 * A dedicated object for delegating notification to the real 'backends'.
 *
 * It has methods for registering and unregistering clients. A client is identified
 * by a unique UUID which gets created and returned during the registration process.
 *
 * A soon as a client is registered it can add and remove notification listeners along
 * with a given {@link ListenerRegistration} which contains the appropriate callback to the backend.
 *
 * A clients needs to ping periodically this agent in order to keep the registrations alive. This
 * is necessary since there is no permanent connection between client and server.
 *
 * @author roland
 * @since 18.03.13
 */
public class NotificationListenerDelegate implements NotificationListener {

    // Managed clients
    private Map<String,ClientConfig> clients;

    // Executor used for registering JMX notification listeners
    private MBeanServerExecutor executor;

    /**
     * Build up this delegate.
     *
     * @param pExecutor executor to used for doing the JMX registrations
     */
    public NotificationListenerDelegate(MBeanServerExecutor pExecutor) {
        executor = pExecutor;
        clients = new HashMap<String, ClientConfig>();
    }

    /**
     * Register a client. A uniqued ID is generated which must be provided in subsequent
     * request
     *
     * @return client id
     */
    public String register() {
        String uuid = findUuid();
        clients.put(uuid,new ClientConfig());
        return uuid;
    }

    /**
     * Unregister the client. All notifications added will be removed from the MBeanServers.
     *
     * @param pClient client to unregister
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    public void unregister(String pClient)
            throws MBeanException, IOException, ReflectionException {
        ClientConfig clientConfig = getClientConfig(pClient);
        for (String handle : clientConfig.getHandles()) {
            removeListener(pClient,handle);
        }
        clients.remove(pClient);
    }

    /**
     * Add a notification listener on behalf of a client. A JMX listener will be added with
     * the filter and handback given in the provided listener configuration.
     *
     * @param pClient client for which to add a listener
     * @param pConfig the listener's configuration
     * @return a handle identifying the registered listener. This handle can be used later for removing the listener.
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    public String addListener(String pClient, final ListenerRegistration pConfig)
            throws MBeanException, IOException, ReflectionException {
        ClientConfig clientConfig = getClientConfig(pClient);
        String handle = clientConfig.add(pConfig);
        executor.each(pConfig.getMBeanName(),new MBeanServerExecutor.MBeanEachCallback() {
            public void callback(MBeanServerConnection pConn, ObjectName pName)
                    throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                pConn.addNotificationListener(pName,NotificationListenerDelegate.this,pConfig.getFilter(),pConfig);
            }
        });
        return handle;
    }

    /**
     * Remove a listener for a given client and for the given handle.
     *
     * @param pClient client for wich to remove the listener
     * @param pHandle the handle as obtained from {@link #addListener(String, ListenerRegistration)}
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    public void removeListener(String pClient, String pHandle)
            throws MBeanException, IOException, ReflectionException {
        ClientConfig clientConfig = getClientConfig(pClient);
        final ListenerRegistration config = clientConfig.get(pHandle);
        executor.each(config.getMBeanName(),new MBeanServerExecutor.MBeanEachCallback() {
            public void callback(MBeanServerConnection pConn, ObjectName pName)
                    throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                try {
                    pConn.removeNotificationListener(pName,NotificationListenerDelegate.this,config.getFilter(),config);
                } catch (ListenerNotFoundException e) {
                    // We tried it. If not there, thats ok, too.
                }
            }
        });
        clientConfig.remove(pHandle);
    }

    /**
     * Refresh a client
     *
     * @param pClient client to refresh
     */
    public void refresh(String pClient) {
        ClientConfig clientConfig = getClientConfig(pClient);
        clientConfig.refresh();
    }

    /**
     * Cleanup all clients which has been refreshed last before the given
     * time in epoch milliseconds
     * @param oldest last refresh timestamp which should be kept
     */
    public void cleanup(long oldest) throws MBeanException, IOException, ReflectionException {
        for (Map.Entry<String,ClientConfig> client : clients.entrySet()) {
            if (client.getValue().getLastRefresh() < oldest) {
                unregister(client.getKey());
            }
        }
    }

    /**
     * Get all registered listeners for a given client
     *
     * @param pClient client for which to get the listener
     * @return map with handle as keys and listener configs as objects.
     */
    public JSONObject list(String pClient) {
        ClientConfig clientConfig = getClientConfig(pClient);
        return clientConfig.list();
    }

    /**
     * {@link NotificationListener} callback method which is used to delegate to the corresponding
     * backend callback.
     *
     * @param notification the original notification received.
     * @param handback as handback which use the listener configuration.
     */
    public void handleNotification(Notification notification, Object handback) {
        ListenerRegistration config = (ListenerRegistration) handback;
        BackendCallback callback = config.getCallback();
        callback.handleNotification(notification,config.getHandback());
    }

    // =========================================================================================================

    // Create a unique client ID
    private String findUuid() {
        UUID uuid;
        do {
            uuid = UUID.randomUUID();
        } while (clients.containsKey(uuid.toString()));
        return uuid.toString();
    }

    // Extract the client config from the internal map and throw and exception
    // if not present
    private ClientConfig getClientConfig(String pClient) {
        ClientConfig clientConfig = clients.get(pClient);
        if (clientConfig == null) {
            throw new IllegalArgumentException("No client " + pClient + " registered");
        }
        return clientConfig;
    }
}
