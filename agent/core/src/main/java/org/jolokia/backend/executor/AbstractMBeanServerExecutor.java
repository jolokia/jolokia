package org.jolokia.backend.executor;

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

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.util.JmxUtil;

/**
 * Base class for providing access to the list of MBeanServer handled by this agent.
 *
 * @author roland
 * @since 22.01.13
 */
public abstract class AbstractMBeanServerExecutor implements MBeanServerExecutor, NotificationListener {

    // Timestamp of last MBeanServer change in milliseconds
    private long lastMBeanRegistrationChange;

    /**
     * Get all MBeanServers
     *
     * @return all MBeanServers in the merge order
     */
    protected abstract Set<MBeanServerConnection> getMBeanServers();

    /**
     * Override this method if you want to provide a Jolokia private MBeanServer. Note, that
     * this method should only return a non-null value, if the Jolokia private MBean Server has
     * some MBeans registered
     *
     * @return the Jolokia MBeanServer
     */
    protected MBeanServerConnection getJolokiaMBeanServer() {
        return null;
    }

    /** {@inheritDoc} */
    public void each(ObjectName pObjectName, MBeanEachCallback pCallback) throws IOException, ReflectionException, MBeanException {
        try {
            Set<ObjectName> visited = new HashSet<ObjectName>();
            for (MBeanServerConnection server : getMBeanServers()) {
                // Query for a full name is the same as a direct lookup
                for (ObjectName nameObject : server.queryNames(pObjectName, null)) {
                    // Don't add if already visited previously
                    if (!visited.contains(nameObject)) {
                        pCallback.callback(server, nameObject);
                        visited.add(nameObject);
                    }
                }
            }
        } catch (InstanceNotFoundException exp) {
            // Well, should not happen, since we do a query before and the returned value are supposed to exist
            // on the mbean-server. But, who knows ...
            throw new IllegalArgumentException("Cannot find MBean " +
                                               (pObjectName != null ? "(MBean " + pObjectName + ")" : "") + ": " + exp,exp);
        }
    }

    /** {@inheritDoc} */
    public <T> T call(ObjectName pObjectName, MBeanAction<T> pMBeanAction, Object ... pExtraArgs)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        InstanceNotFoundException objNotFoundException = null;
        for (MBeanServerConnection server : getMBeanServers()) {
            // Only the first MBeanServer holding the MBean wins
            try {
                // Still to decide: Should we check eagerly or let an InstanceNotFound Exception
                // bubble ? Exception bubbling was the former behaviour, so it is left in. However,
                // it would be interesting how large the performance impact is here. All unit tests BTW are
                // prepared for switching the guard below on or off.

                //if (server.isRegistered(pObjectName)) {
                return pMBeanAction.execute(server, pObjectName, pExtraArgs);
                //}
            } catch (InstanceNotFoundException exp) {
                // Remember exceptions for later use
                objNotFoundException = exp;
            }
        }

        // Must be there, otherwise we would not have left the loop
        throw objNotFoundException;

        // When we reach this, no MBeanServer know about the requested MBean.
        // Hence, we throw our own InstanceNotFoundException here

        //throw exception != null ?
        //        new IllegalArgumentException(errorMsg + ": " + exception,exception) :
        //        new IllegalArgumentException(errorMsg);
    }

    /** {@inheritDoc} */
    public Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException {
        Set<ObjectName> names = new LinkedHashSet<ObjectName>();
        for (MBeanServerConnection server : getMBeanServers()) {
            names.addAll(server.queryNames(pObjectName,null));
        }
        return names;
    }

    /**
     * Add this executor as listener for MBeanServer notification so that we can update
     * the local timestamp for when the set of registered MBeans has changed last.
     */
    protected void registerForMBeanNotifications() {
        Set<MBeanServerConnection> servers = getMBeanServers();
        Exception lastExp = null;
        StringBuilder errors = new StringBuilder();
        for (MBeanServerConnection server : servers) {
            try {
                JmxUtil.addMBeanRegistrationListener(server,this,null);
            } catch (IllegalStateException e) {
                lastExp = updateErrorMsg(errors,e);
            }
        }
        if (lastExp != null) {
            throw new IllegalStateException(errors.substring(0,errors.length()-1),lastExp);
        }
    }

    /**
     * Unregister us as listener from every registered server
     */
    public void destroy() {
        Set<MBeanServerConnection> servers = getMBeanServers();
        Exception lastExp = null;
        StringBuilder errors = new StringBuilder();
        for (MBeanServerConnection server : servers) {
            try {
                JmxUtil.removeMBeanRegistrationListener(server,this);
            } catch (IllegalStateException e) {
                lastExp = updateErrorMsg(errors, e);
            }
        }
        if (lastExp != null) {
            throw new IllegalStateException(errors.substring(0,errors.length()-1),lastExp);
        }
    }

    /** {@inheritDoc} */
    // Remember current timestamp
    public void handleNotification(Notification pNotification, Object pHandback) {
        // Update timestamp
        lastMBeanRegistrationChange = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    public boolean hasMBeansListChangedSince(long pTimestamp) {
        return (lastMBeanRegistrationChange / 1000) >= pTimestamp;
    }

    // Helper method for adding the exception for an appropriate error message
    private Exception updateErrorMsg(StringBuilder pErrors, Exception exp) {
        pErrors.append(exp.getClass()).append(": ").append(exp.getMessage()).append("\n");
        return exp;
    }
}
