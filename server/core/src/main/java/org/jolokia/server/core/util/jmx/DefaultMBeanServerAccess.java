/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.util.jmx;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * Base class for providing access to the list of MBeanServer handled by this agent.
 *
 * @author roland
 * @since 22.01.13
 */
public class DefaultMBeanServerAccess implements MBeanServerAccess, NotificationListener {

    // Timestamp of last MBeanServer change in milliseconds
    private long lastMBeanRegistrationChange;

    // Wrapped MBeanServers to handle the available MBean server connections
    private final MBeanServers mbeanServers;

    /**
     * Constructor using default MBeanServers
     */
    public DefaultMBeanServerAccess() {
        this(null);
    }

    /**
     * Create an MBeanServer executor for calling MBeanServers
     *
     * @param pServers mbean servers to wrap and call
     */
    public DefaultMBeanServerAccess(Set<MBeanServerConnection> pServers) {
        mbeanServers = new MBeanServers(pServers, this);

        // Register for registers/deregister of MBean changes in order to update lastUpdateTime
        registerForMBeanNotifications(mbeanServers);
    }

    // ---- org.jolokia.server.core.util.jmx.MBeanServerAccess

    @Override
    public Set<MBeanServerConnection> getMBeanServers() {
        // this includes the platform and autodetected MBeanServers in addition to what was
        // passed in the constructor
        return mbeanServers.getMBeanServers();
    }

    @Override
    public void each(ObjectName pObjectName, MBeanEachCallback pCallback) throws IOException, JMException {
        boolean pattern = pObjectName != null && pObjectName.isPattern();

        InstanceNotFoundException instanceNotFoundException = null;
        Set<ObjectName> visited = new HashSet<>();

        for (MBeanServerConnection server : getMBeanServers()) {
            // Query for a full name is the same as a direct lookup
            for (ObjectInstance instance : server.queryMBeans(pObjectName, null)) {
                // Don't add if already visited previously - while single server has unique MBeans,
                // we may get the same MBean from different MBeanServerConnections - first wins
                if (!visited.contains(instance.getObjectName())) {
                    try {
                        pCallback.callback(server, instance);
                        visited.add(instance.getObjectName());
                    } catch (InstanceNotFoundException exp) {
                        if (pattern) {
                            // the instance may already be gone, so ignore
                            continue;
                        }
                        // we record the exception, so it's thrown if no server was successfully called
                        instanceNotFoundException = exp;
                    }
                }
            }
        }

        if (!pattern && instanceNotFoundException != null) {
            // we don't throw InstanceNotFoundException if the object was a pattern, because pattern usually
            // means "ignore if nothing matched"
            throw instanceNotFoundException;
        }
    }

    @Override
    public <T> T call(ObjectName pObjectName, MBeanAction<T> pAction, Object ... pExtraArgs) throws IOException, JMException {
        InstanceNotFoundException instanceNotFoundException = null;

        for (MBeanServerConnection server : getMBeanServers()) {
            // The first MBeanServer holding the MBean wins, so no need to track the instances in a Set
            try {
                return pAction.execute(server, pObjectName, pExtraArgs);
            } catch (InstanceNotFoundException exp) {
                // Remember exceptions for later use
                instanceNotFoundException = exp;
            }
        }

        // Must be != null, otherwise we would not have left the loop
        throw Objects.requireNonNull(instanceNotFoundException);
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException {
        Set<ObjectName> names = new LinkedHashSet<>();
        for (MBeanServerConnection server : getMBeanServers()) {
            names.addAll(server.queryNames(pObjectName, null));
        }
        return names;
    }

    @Override
    public boolean hasMBeansListChangedSince(long pTimestamp) {
        return (lastMBeanRegistrationChange / 1000) >= pTimestamp;
    }

    // ---- javax.management.NotificationListener

    @Override
    public void handleNotification(Notification pNotification, Object pHandback) {
        // Update timestamp
        lastMBeanRegistrationChange = System.currentTimeMillis();
    }

    // ---- helper methods

    /**
     * Override this method if you want to provide a Jolokia private MBeanServer. Note, that
     * this method should only return a non-null value, if the Jolokia private MBean Server has
     * some MBeans registered
     *
     * @return the Jolokia MBeanServer
     */
    protected MBeanServerConnection getJolokiaMBeanServer() {
        return mbeanServers.getJolokiaMBeanServer();
    }

    /**
     * Add this executor as listener for MBeanServer notification so that we can update
     * the local timestamp for when the set of registered MBeans has changed last.
     */
    private void registerForMBeanNotifications(MBeanServers pServers) {
        Exception lastExp = null;
        StringBuilder errors = new StringBuilder();
        for (MBeanServerConnection server : pServers.getMBeanServers()) {
            try {
                JmxUtil.addMBeanRegistrationListener(server, this, null);
            } catch (IllegalStateException e) {
                lastExp = updateErrorMsg(errors, e);
            }
        }
        if (lastExp != null) {
            throw new IllegalStateException(errors.substring(0, errors.length() - 1), lastExp);
        }
    }

    /**
     * Unregister us as listener from every registered server
     */
    public void unregisterFromMBeanNotifications() {
        Set<MBeanServerConnection> servers = getMBeanServers();
        Exception lastExp = null;
        StringBuilder errors = new StringBuilder();
        for (MBeanServerConnection server : servers) {
            try {
                JmxUtil.removeMBeanRegistrationListener(server, this);
            } catch (IllegalStateException e) {
                lastExp = updateErrorMsg(errors, e);
            }
        }
        if (lastExp != null) {
            throw new IllegalStateException(errors.substring(0, errors.length() - 1), lastExp);
        }
        mbeanServers.destroy();
    }

    // Helper method for adding the exception for an appropriate error message
    private Exception updateErrorMsg(StringBuilder pErrors, Exception exp) {
        pErrors.append(exp.getClass()).append(": ").append(exp.getMessage()).append("\n");
        return exp;
    }

    // Dump all known MBeanServers, delegate to wrapper
    public String dumpMBeanServers() {
        return mbeanServers.dump();
    }

}
