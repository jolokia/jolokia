package org.jolokia.backend;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.jmx.MBeanServerExecutor;

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
     * @param withJolokiaMBeanServer whether to include the Jolokia MBeanServer in the set or not. However, the
     *                               Jolokia MBeanServer is only included if there are MBeans registered there.
     * @return all MBeanServers possibly with the Jolokia MBean Server included.
     */
    protected abstract Set<MBeanServerConnection> getMBeanServers(boolean withJolokiaMBeanServer);

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
            // MBean pattern for an MBean can match at multiple servers
            MBeanServerConnection jolokiaServer = getJolokiaMBeanServer();
            Set<ObjectName> jolokiaMBeans = null;
            if (jolokiaServer != null) {
                jolokiaMBeans = new HashSet<ObjectName>();
                for (ObjectName nameObject : jolokiaServer.queryNames(pObjectName,null)) {
                    pCallback.callback(jolokiaServer, nameObject);
                    jolokiaMBeans.add(pObjectName);
                }
            }
            for (MBeanServerConnection server : getMBeanServers(false)) {
                // Query for a full name is the same as a direct lookup
                for (ObjectName nameObject : server.queryNames(pObjectName,null)) {
                    // Dont add if already present in the Jolokia MBeanServer
                    if (jolokiaMBeans == null || !jolokiaMBeans.contains(pObjectName)) {
                        pCallback.callback(server, nameObject);
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
            throws IOException, ReflectionException, MBeanException {
        Exception exception = null;
        for (MBeanServerConnection server : getMBeanServers(true)) {
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
        for (MBeanServerConnection server : getMBeanServers(true)) {
            names.addAll(server.queryNames(pObjectName,null));
        }
        return names;
    }
}
