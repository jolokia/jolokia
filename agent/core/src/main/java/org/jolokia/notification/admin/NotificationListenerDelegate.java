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

package org.jolokia.notification.admin;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.notification.BackendCallback;
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
    private Map<String, Client> clients;

    /**
     * Build up this delegate.
     */
    public NotificationListenerDelegate() {
        clients = new HashMap<String, Client>();
    }

    /**
     * Register a client. A uniqued ID is generated which must be provided in subsequent
     * request
     *
     * @return client id
     */
    public String register() {
        String uuid = createClientId();
        clients.put(uuid, new Client(uuid));
        return uuid;
    }

    /**
     * Unregister the client. All notifications added will be removed from the MBeanServers.
     *
     * @param pExecutor the executor used for unregistering listeners
     * @param pClient client to unregister
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    public void unregister(MBeanServerExecutor pExecutor, String pClient)
            throws MBeanException, IOException, ReflectionException {
        Client client = getClient(pClient);
        for (String handle : client.getHandles()) {
            removeListener(pExecutor, pClient, handle);
        }
        clients.remove(pClient);
    }

    /**
     * Add a notification listener on behalf of a client. A JMX listener will be added with
     * the filter and handback given in the provided listener configuration.
     *
     * @param pExecutor executor for registering JMX notification listeners
     * @param pClient client for which to add a listener
     * @param pRegistration the listener's configuration
     * @return a handle identifying the registered listener. This handle can be used later for removing the listener.
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    public String addListener(MBeanServerExecutor pExecutor, String pClient, final ListenerRegistration pRegistration)
            throws MBeanException, IOException, ReflectionException {
        Client client = getClient(pClient);
        String handle = client.add(pRegistration);
        pExecutor.each(pRegistration.getMBeanName(),new MBeanServerExecutor.MBeanEachCallback() {
            /** {@inheritDoc} */
            public void callback(MBeanServerConnection pConn, ObjectName pName)
                    throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                pConn.addNotificationListener(pName,NotificationListenerDelegate.this,pRegistration.getFilter(),pRegistration);
            }
        });
        return handle;
    }

    /**
     * Remove a listener for a given client and for the given handle.
     *
     * @param pExecutor executor for removing JMX notifications listeners
     * @param pClient client for wich to remove the listener
     * @param pHandle the handle as obtained from {@link #addListener(MBeanServerExecutor, String, ListenerRegistration)}
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    public void removeListener(MBeanServerExecutor pExecutor, String pClient, String pHandle)
            throws MBeanException, IOException, ReflectionException {
        Client client = getClient(pClient);
        final ListenerRegistration registration = client.get(pHandle);
        pExecutor.each(registration.getMBeanName(),new MBeanServerExecutor.MBeanEachCallback() {
            /** {@inheritDoc} */
            public void callback(MBeanServerConnection pConn, ObjectName pName)
                    throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                try {
                    pConn.removeNotificationListener(pName, NotificationListenerDelegate.this, registration.getFilter(), registration);
                } catch (ListenerNotFoundException e) {
                    // We tried it. If not there, thats ok, too.
                }
            }
        });
        client.remove(pHandle);
    }

    /**
     * Refresh a client
     *
     * @param pClient client to refresh
     */
    public void refresh(String pClient) {
        Client client = getClient(pClient);
        client.refresh();
    }

    /**
     * Cleanup all clients which has been refreshed last before the given
     * time in epoch milliseconds
     *
     * @param pExecutor executor used for unregistering
     * @param pOldest last refresh timestamp which should be kept
     */
    public void cleanup(MBeanServerExecutor pExecutor, long pOldest) throws MBeanException, IOException, ReflectionException {
        for (Map.Entry<String,Client> client : clients.entrySet()) {
            if (client.getValue().getLastRefresh() < pOldest) {
                unregister(pExecutor, client.getKey());
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
        Client client = getClient(pClient);
        return client.list();
    }

    /**
     * {@link NotificationListener} callback method which is used to delegate to the corresponding
     * backend callback.
     *
     * @param notification the original notification received.
     * @param handback as handback which use the listener configuration.
     */
    public void handleNotification(Notification notification, Object handback) {
        ListenerRegistration registration = (ListenerRegistration) handback;
        BackendCallback callback = registration.getCallback();
        callback.handleNotification(notification, registration.getHandback());
    }

    // =========================================================================================================

    // Create a unique client ID
    private String createClientId() {
        return UUID.randomUUID().toString();
    }

    // Extract the client config from the internal map and throw and exception
    // if not present
    private Client getClient(String pClient) {
        Client client = clients.get(pClient);
        if (client == null) {
            throw new IllegalArgumentException("No client " + pClient + " registered");
        }
        return client;
    }
}
