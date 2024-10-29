package org.jolokia.server.core.util.jmx;

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import static javax.management.MBeanServerNotification.*;

/**
 * Class managing the set of available MBeanServers
 */
public class MBeanServers implements NotificationListener {

    // All detected MBeanServces
    private final Set<MBeanServerConnection> detectedMBeanServers;

    // detectedMBeanServers + JolokiaMBeanServer (if available)
    private final Set<MBeanServerConnection> allMBeanServers;

    // Notification listener to register to the JolokiaMBean server
    // which listens for MBean registration events
    private final NotificationListener jolokiaMBeanServerListener;

    // Private Jolokia MBeanServer
    private MBeanServerConnection jolokiaMBeanServer;

    /**
     * Name of MBean used for registration of Jolokia own {@link MBeanServer}
     */
    public static final String JOLOKIA_MBEAN_SERVER_NAME = "jolokia:type=MBeanServer";

    private static final ObjectName JOLOKIA_MBEAN_SERVER_ONAME = JmxUtil.newObjectName(JOLOKIA_MBEAN_SERVER_NAME);

    /**
     * Constructor building up the list of available MBeanServers
     *
     * @param pExtraServers list of extra MBeanServers, possibly detected elsewhere
     * @param pListener listener to register to the Jolokia MBeanServer when this server
     */
    MBeanServers(Set<MBeanServerConnection> pExtraServers, NotificationListener pListener) {
        detectedMBeanServers = new LinkedHashSet<>();
        jolokiaMBeanServerListener = pListener;

        // Create and add our own JolokiaMBeanServer first and
        // add us for registration/deregistration of the MBeanServer
        jolokiaMBeanServer = lookupJolokiaMBeanServer();
        addJolokiaMBeanServerRegistrationListener();

        if (pExtraServers != null) {
            detectedMBeanServers.addAll(pExtraServers);
        }

        // All MBean Server known by the MBeanServerFactory
        List<MBeanServer> beanServers = MBeanServerFactory.findMBeanServer(null);
        detectedMBeanServers.addAll(beanServers);

        // Last entry is always the platform MBeanServer
        detectedMBeanServers.add(ManagementFactory.getPlatformMBeanServer());

        allMBeanServers = new LinkedHashSet<>();
        if (jolokiaMBeanServer != null) {
            allMBeanServers.add(jolokiaMBeanServer);
        }
        allMBeanServers.addAll(detectedMBeanServers);
    }

    /**
     * Fetch Jolokia MBeanServer when it gets registered, remove it if being unregistered
     *
     * @param notification notification emitted
     * @param handback not used here
     */
    public synchronized void handleNotification(Notification notification, Object handback) {
        String type = notification.getType();
        if (REGISTRATION_NOTIFICATION.equals(type)) {
            jolokiaMBeanServer = lookupJolokiaMBeanServer();
            // We need to add the listener provided during construction time to add the Jolokia MBeanServer
            // so that it is kept updated, too.
            if (jolokiaMBeanServerListener != null) {
                JmxUtil.addMBeanRegistrationListener(jolokiaMBeanServer, jolokiaMBeanServerListener, null);
            }
        } else if (UNREGISTRATION_NOTIFICATION.equals(type)) {
            jolokiaMBeanServer = null;
        }

        allMBeanServers.clear();
        if (jolokiaMBeanServer != null) {
            allMBeanServers.add(jolokiaMBeanServer);
        }
        allMBeanServers.addAll(detectedMBeanServers);
    }

    /**
     * Get the list of the current set of MBeanServers which are active
     * @return the set of current MBeanServers
     */
    public Set<MBeanServerConnection> getMBeanServers() {
        return allMBeanServers;
    }

    /**
     * Return a dump information of all known MBeanServers
     *
     * @return information about the registered MBeanServers
     */
    public String dump() {
        return MBeanServerDumper.dump(allMBeanServers);
    }

    /**
     * To be called during deconstruction.
     */
    public void destroy() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxUtil.removeMBeanRegistrationListener(server,this);
    }

    /**
     * Get the Jolokia MBean server
     * @return Jolokia MBeanServer or null
     */
    public MBeanServerConnection getJolokiaMBeanServer() {
        return jolokiaMBeanServer;
    }

    // ======================================================================================================

    // Check, whether the Jolokia MBean Server is available and return it.
    private MBeanServer lookupJolokiaMBeanServer() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            return server.isRegistered(JOLOKIA_MBEAN_SERVER_ONAME) ?
                    (MBeanServer) server.getAttribute(JOLOKIA_MBEAN_SERVER_ONAME, "JolokiaMBeanServer") :
                    null;
        } catch (JMException e) {
            throw new IllegalStateException("Internal: Cannot get Jolokia MBeanServer via JMX lookup: " + e, e);
        }
    }

    // Register this executor as notification listener for the JolokiaMBeanServer at the PlatformMBeanServer
    private void addJolokiaMBeanServerRegistrationListener() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxUtil.addMBeanRegistrationListener(server,this,JOLOKIA_MBEAN_SERVER_ONAME);
    }
}
