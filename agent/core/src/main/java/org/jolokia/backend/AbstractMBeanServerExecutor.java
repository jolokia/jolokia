package org.jolokia.backend;

import java.io.IOException;
import java.util.*;

import javax.management.*;

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

    public <R> void iterate(ObjectName pObjectName, MBeanAction<R> pMBeanAction, Object ... pExtraArgs) throws IOException, ReflectionException, MBeanException {
        try {
        if (pObjectName == null || pObjectName.isPattern()) {
            // MBean pattern for an MBean can match at multiple servers
            for (MBeanServerConnection server : getMBeanServers()) {
                for (Object nameObject : server.queryNames(pObjectName,null)) {
                    pMBeanAction.execute(server, (ObjectName) nameObject, pExtraArgs);
                }
            }
        } else {
            // Add a single named MBean's information to the handler
            for (MBeanServerConnection server : getMBeanServers()) {
                // Only the first MBeanServer holding the MBean wins
                if (server.isRegistered(pObjectName)) {
                    pMBeanAction.execute(server, pObjectName);
                    return;
                }
            }
            throw new IllegalArgumentException("No MBean with ObjectName " + pObjectName + " is registered");
        }
        } catch (InstanceNotFoundException exp) {
            throw new IllegalArgumentException("Cannot find MBean " +
                                               (pObjectName != null ? "(MBean " + pObjectName + ")" : "") + ": " + exp,exp);
        } catch (AttributeNotFoundException exp) {
            throw new IllegalArgumentException("Cannot handle MBean attribute " +
                                               (pObjectName != null ? "(MBean " + pObjectName + ")" : "") + ": " + exp,exp);
        }
    }

    public <T> T callFirst(ObjectName pObjectName, MBeanAction<T> pMBeanAction, Object... pExtraArgs)
            throws IOException, ReflectionException, MBeanException {
        Exception exception = null;
        for (MBeanServerConnection server : getMBeanServers()) {
            // Only the first MBeanServer holding the MBean wins
            try {
                return pMBeanAction.execute(server, pObjectName, pExtraArgs);
            } catch (InstanceNotFoundException exp) {
                exception = exp;
                // Try next one ...
            } catch (AttributeNotFoundException exp) {
                // Try next one, too ..
                exception = exp;
            }
        }
        // When we reach this, no MBeanServer know about the requested MBean.
        // Hence, we throw our own InstanceNotFoundException here
        throw new IllegalArgumentException("No MBean with ObjectName " + pObjectName + " and attribute is registered: " + exception,
                                           exception);
    }

    public Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException {
        Set<ObjectName> names = new LinkedHashSet<ObjectName>();
        for (MBeanServerConnection server : getMBeanServers()) {
            names.addAll(server.queryNames(pObjectName,null));
        }
        return names;
    }
}
