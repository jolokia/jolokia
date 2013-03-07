package org.jolokia.util;

import java.io.IOException;

import javax.management.*;
import javax.management.relation.MBeanServerNotificationFilter;

/**
 * Utilit class for dealing with JMX's {@link ObjectName}
 *
 * @author roland
 * @since 05.03.13
 */
public final class JmxUtil {

    // Utility class with static methods
    private JmxUtil() {}

    /**
     * Factory method for creating a new object name, mapping any checked {@link MalformedObjectNameException} to
     * a runtime exception ({@link IllegalArgumentException})
     * @param pName name to convert
     * @return the created object name
     */
    public static ObjectName newObjectName(String pName) {
        try {
            return new ObjectName(pName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name " + pName,e);
        }
    }

    /**
     * Register a notification listener which listens for registration and deregistration of MBeans at a certain server
     *
     * @param pServer server to register to
     * @param pListener listener to register
     * @param pObjectNameToFilter object name which should be listen for. If null, listens for any MBean registration
     */
    public static void addMBeanRegistrationListener(MBeanServerConnection pServer, NotificationListener pListener,
                                                    ObjectName pObjectNameToFilter) {
        MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
        if (pObjectNameToFilter == null) {
            filter.enableAllObjectNames();
        } else {
            filter.enableObjectName(pObjectNameToFilter);
        }
        try {
            pServer.addNotificationListener(getMBeanServerDelegateName(), pListener, filter, null);
        } catch (InstanceNotFoundException e) {
            throw new IllegalStateException("Cannot find " + getMBeanServerDelegateName() + " in server " + pServer,e);
        } catch (IOException e) {
            throw new IllegalStateException("IOException while registering notification listener for " + getMBeanServerDelegateName(),e);
        }
    }

    /**
     * Remove a notification listener from the given MBeanServer while listening for MBeanServer registration events
     * @param pServer server from where to unregister
     * @param pListener listener to unregister
     */
    public static void removeMBeanRegistrationListener(MBeanServerConnection pServer,NotificationListener pListener) {
        try {
            pServer.removeNotificationListener(JmxUtil.getMBeanServerDelegateName(), pListener);
        } catch (ListenerNotFoundException e) {
            // We silently ignore listeners not found, they might have been deregistered previously
        } catch (InstanceNotFoundException e) {
            throw new IllegalStateException("Cannot find " + getMBeanServerDelegateName() + " in server " + pServer,e);
        } catch (IOException e) {
            throw new IllegalStateException("IOException while registering notification listener for " + getMBeanServerDelegateName(),e);
        }

    }

    /**
     * Lookup the server delegate name, which works for sure for Java 1.6 but maye not for Java 1.5.
     * This method should be removed when dropping Java 1.5 support
     * @return the objectname of the MBeanServer delegate present in every MBeanServer
     */
    private static ObjectName getMBeanServerDelegateName() {
        try {
            return MBeanServerDelegate.DELEGATE_NAME;
        } catch (NoSuchFieldError error) {
            // For Java 1.5 we return the fixed name
            return newObjectName("JMImplementation:type=MBeanServerDelegate");
        }
    }
}
