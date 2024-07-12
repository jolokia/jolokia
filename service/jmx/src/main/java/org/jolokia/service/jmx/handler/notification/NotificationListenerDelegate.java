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

package org.jolokia.service.jmx.handler.notification;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.http.BackChannel;
import org.jolokia.server.core.http.BackChannelHolder;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.notification.OpenCommand;
import org.jolokia.server.core.service.notification.*;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.request.notification.AddCommand;
import org.jolokia.json.JSONObject;

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
class NotificationListenerDelegate implements NotificationListener {

    // Managed clients
    private final Map<String, Client> clients;

    // Backendmanager holding the backends
    private final NotificationBackendManager backendManager;

    /**
     * Build up this delegate.
     * @param pBackendManager
     */
    NotificationListenerDelegate(NotificationBackendManager pBackendManager) {
        backendManager = pBackendManager;
        clients = new HashMap<>();
    }

    /**
     * Register a client. A unique ID is generated which must be provided in subsequent
     * request
     *
     * @return client id
     */
    String register() {
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
    void unregister(MBeanServerAccess pExecutor, String pClient)
            throws MBeanException, IOException, ReflectionException {
        Client client = getClient(pClient);
        for (String handle : client.getHandles()) {
            removeListener(pExecutor, pClient, handle);
        }
        clients.remove(pClient);
        backendManager.unregister(client);
    }

    /**
     * Add a notification listener on behalf of a client. A JMX listener will be added with
     * the filter and handback given in the provided listener configuration.
     *
     * The command has the following properties:
     * <ul>
     *     <li>
     *         <b>client</b> client id identifying the current client.
     *     </li>
     *     <li>
     *         <b>mbean</b> the MBean on which to register the listener
     *     </li>
     *     <li>
     *         <b>mode</b> specifies the notification backend/model.
     *         Something like "pull", "sockjs", "ws" or "push"
     *     </li>
     *     <li>
     *         Optional <b>filter</b> and <b>handback</b>
     *     </li>
     * </ul>
     *
     * @param pExecutor executor for registering JMX notification listeners
     * @param pCommand original AddCommand as received bt Jolokia
     * @return a handle identifying the registered listener. This handle can be used later for removing the listener.
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    String addListener(MBeanServerAccess pExecutor, final AddCommand pCommand)
            throws MBeanException, IOException, ReflectionException {

        // Fetch client and backend
        Client client = getClient(pCommand.getClient());

        // Get backend and remember that the client is using it
        NotificationBackend backend = backendManager.getBackend(pCommand.getMode());

        // The tricky part: Can a handle before, then subscribe with the handle at the backend, then register as JMX
        // listener
        synchronized (client) {
            String handle = client.getNextHandle();
            NotificationSubscription notificationSubscription = new NotificationSubscriptionImpl(handle,pCommand,client,this);
            BackendCallback callback = backend.subscribe(notificationSubscription);
            final ListenerRegistration listenerRegistration = new ListenerRegistration(pCommand,callback);
            client.addUsedBackend(pCommand.getMode());
            client.addNotification(handle, listenerRegistration);

            // Register as JMX notification listener
            final boolean[] added = new boolean[] { false };
            try {
                pExecutor.each(listenerRegistration.getMBeanName(),new MBeanServerAccess.MBeanEachCallback() {
                    /** {@inheritDoc} */
                    public void callback(MBeanServerConnection pConn, ObjectName pName)
                            throws InstanceNotFoundException, IOException {
                        pConn.addNotificationListener(pName,NotificationListenerDelegate.this,
                                                      listenerRegistration.getFilter(),listenerRegistration);
                        added[0] = true;
                    }
                });
            } finally {
                if (!added[0]) {
                    // Remove it if exception has been thrown
                    client.remove(handle);
                }
            }
            return handle;
        }
    }

    /**
     * Remove a listener for a given client and for the given handle.
     *
     * @param pExecutor executor for removing JMX notifications listeners
     * @param pClient client for wich to remove the listener
     * @param pHandle the handle as obtained from {@link #addListener(MBeanServerAccess, AddCommand)}
     *
     * @throws MBeanException
     * @throws IOException
     * @throws ReflectionException
     */
    void removeListener(MBeanServerAccess pExecutor, String pClient, String pHandle)
            throws MBeanException, IOException, ReflectionException {
        Client client = getClient(pClient);
        final ListenerRegistration registration = client.get(pHandle);
        pExecutor.each(registration.getMBeanName(), new MBeanServerAccess.MBeanEachCallback() {
            /** {@inheritDoc} */
            public void callback(MBeanServerConnection pConn, ObjectName pName) throws InstanceNotFoundException, IOException {
                try {
                    pConn.removeNotificationListener(pName, NotificationListenerDelegate.this, registration.getFilter(), registration);
                } catch (ListenerNotFoundException e) {
                    // We tried it. If not there, thats ok, too.
                }
            }
        });
        client.remove(pHandle);
        backendManager.unsubscribe(registration.getBackendMode(), pClient, pHandle);
    }

    /**
     * Updated freshness of this client. Since we use a stateless model, the server
     * needs to know somehow, when a client fades away without unregistering all its
     * listeners. The idea is, that a client have to send a ping within certain
     * intervals and if this ping is missing, the client is considered as stale and
     * all its listeners get removed automatically then.
     *
     * @param pClient client to update.
     */
    void refresh(String pClient) {
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
    void cleanup(MBeanServerAccess pExecutor, long pOldest)
            throws MBeanException, IOException, ReflectionException {
        for (Map.Entry<String,Client> client : clients.entrySet()) {
            if (client.getValue().getLastRefresh() < pOldest) {
                unregister(pExecutor,client.getKey());
            }
        }
    }

    /**
     * Open a new back channel and attach it to a client.
     * An already active back channel for this client is closed before.
     *
     * The back channel is obtained
     * @param pCommand command used for opening this channel
     */
    void openChannel(OpenCommand pCommand) throws IOException, EmptyResponseException {
        String clientId = pCommand.getClient();
        Client client = getClient(clientId);
        String mode = pCommand.getMode();
        synchronized (client) {
            BackChannel channel = client.getBackChannel(mode);
            if (channel != null) {
                channel.close();
            }
            NotificationBackend backend = backendManager.getBackend(mode);
            channel = BackChannelHolder.get();
            channel.open(backend.getConfig());
            backend.channelInit(client, channel);
            client.setBackChannel(mode, channel);
        }
        throw new EmptyResponseException();
    }


    /**
     * List all listener registered by a client along with its configuration parameters
     *
     * @param pClient client for which to get the listener
     * @return a JSON object describing all listeners. Keys are probably the handles
     *         created during addListener(). Values are the configuration of the
     *         listener jobs.
     */
    JSONObject list(String pClient) {
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
