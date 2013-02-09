package org.jolokia.backend.executor;

import java.io.IOException;
import java.util.*;

import javax.management.*;

/**
 * Base class for providing access to the list of MBeanServer handled by this agent.
 *
 * @author roland
 * @since 22.01.13
 */
public abstract class AbstractMBeanServerExecutor implements MBeanServerExecutor {

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
                for (ObjectName nameObject : server.queryNames(pObjectName,null)) {
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

}
