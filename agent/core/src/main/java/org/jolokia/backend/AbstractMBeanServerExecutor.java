package org.jolokia.backend;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.jmx.MBeanServerExecutor;

/**
 * @author roland
 * @since 22.01.13
 */
public abstract class AbstractMBeanServerExecutor implements MBeanServerExecutor {

    /**
     * Get all active MBeanServer, i.e. excluding the Jolokia MBean Server
     * if it has no MBean attached yet.
     *
     * @return active MBeanServers
     */
    protected abstract Set<MBeanServerConnection> getMBeanServers();

    /** {@inheritDoc} */
    public void each(ObjectName pObjectName, MBeanEachCallback pCallback) throws IOException, ReflectionException, MBeanException {
        try {
            // MBean pattern for an MBean can match at multiple servers
            for (MBeanServerConnection server : getMBeanServers()) {
                // Query for a full name is the same as a direct lookup
                for (ObjectName nameObject : server.queryNames(pObjectName,null)) {
                    pCallback.callback(server, nameObject);
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
            throws IOException, ReflectionException, MBeanException {
        Exception exception = null;
        for (MBeanServerConnection server : getMBeanServers()) {
            // Only the first MBeanServer holding the MBean wins
            try {
                // Still to decide: Should we check eagerly or let an InstanceNotFound Exception
                // bubble ? Exception bubbling was the former behaviour, so it is left in. However,
                // it would be interesting how the performance impact is here. All tests BTW are
                // prepared for switching the guard below on.
                //if (server.isRegistered(pObjectName)) {
                    return pMBeanAction.execute(server, pObjectName, pExtraArgs);
                //}
            } catch (InstanceNotFoundException exp) {
                // Should not happen, since we check beforehand
                exception = exp;
            } catch (AttributeNotFoundException exp) {
                // Try next one, too ..
                exception = exp;
            }
        }
        // When we reach this, no MBeanServer know about the requested MBean.
        // Hence, we throw our own InstanceNotFoundException here

        String errorMsg = "No MBean with ObjectName " + pObjectName + " and attribute is registered";
        throw exception != null ?
                new IllegalArgumentException(errorMsg + ": " + exception,exception) :
                new IllegalArgumentException(errorMsg);
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
