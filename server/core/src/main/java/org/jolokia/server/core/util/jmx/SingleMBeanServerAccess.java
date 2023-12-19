package org.jolokia.server.core.util.jmx;

import java.io.IOException;
import java.util.*;

import javax.management.*;

/**
 * A simple executor which uses only a single connection. It does not support
 * update change detection.
 *
 * @author roland
 * @since 14.01.14
 */
public class SingleMBeanServerAccess implements MBeanServerAccess {

    private final MBeanServerConnection connection;

    /**
     * Constructor for wrapping a remote connection
     * @param pConnection remote connection to wrap
     */
    public SingleMBeanServerAccess(MBeanServerConnection pConnection) {
        connection = pConnection;
    }

    public void each(ObjectName pObjectName, MBeanEachCallback pCallback) throws IOException, ReflectionException, MBeanException {
        try {
            for (ObjectName nameObject : connection.queryNames(pObjectName, null)) {
                pCallback.callback(connection, nameObject);
             }
         } catch (InstanceNotFoundException exp) {
             // Something which is not plausible and should not happen (remember, we do a query before)
             throw new IllegalArgumentException("Cannot find MBean " +
                                               (pObjectName != null ? "(MBean " + pObjectName + ")" : "") + ": " + exp,exp);
        }
    }

    public <R> R call(ObjectName pObjectName, MBeanAction<R> pMBeanAction, Object... pExtraArgs) throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        return pMBeanAction.execute(connection, pObjectName, pExtraArgs);
    }

    public Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException {
        return connection.queryNames(pObjectName, null);
    }

    public void destroy() { }

    public boolean hasMBeansListChangedSince(long pTimestamp) {
        return true;
    }

    /** {@inheritDoc} */
    public Set<MBeanServerConnection> getMBeanServers() {
        return Collections.singleton(connection);
    }

}
