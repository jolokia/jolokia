/*
 * Copyright 2009-2013  Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.backend;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;
import javax.management.relation.MBeanServerNotificationFilter;

import org.jolokia.backend.executor.AbstractMBeanServerExecutor;
import org.jolokia.detector.ServerDetector;
import org.jolokia.handler.JsonRequestHandler;
import org.jolokia.request.JmxRequest;
import org.jolokia.util.ServersInfo;

/**
 * Singleton responsible for doing the merging of all MBeanServer detected.
 * It provides a single entry point for all supported JMX operations and has the
 * facility to detect MBeanServers by delegating the lookup to various
 * {@link ServerDetector}s, to {@link MBeanServerFactory#findMBeanServer(String)}
 * and finally to the PlatformMBeanServer
 *
 *
 * It also has a special treatment for the so called "JolokiaMBeanServer" which is
 * always hidden from JSR-160 and provides some extra functionality like JSONMBeans.
 *
 * @author roland
 * @since 17.01.13
 */
public class MBeanServerExecutorLocal extends AbstractMBeanServerExecutor implements NotificationListener {

    // Private Jolokia MBeanServer
    private MBeanServer jolokiaMBeanServer;

    // Set of detected MBeanSevers
    private Set<MBeanServerConnection> mBeanServers;

    // All MBeanServers including the JolokiaMBeanServer
    private Set<MBeanServerConnection> allMBeanServers;

    /**
     * Constructor with a given list of destectors
     *
     * @param pDetectors list of detectors for the MBeanServers. Must not be null.
     */
    public MBeanServerExecutorLocal(List<ServerDetector> pDetectors) {
        init(pDetectors);
    }

    /**
     * Constructor with no detectors
     */
    public MBeanServerExecutorLocal() {
        this(Collections.<ServerDetector>emptyList());
    }

    /**
     * Use various ways for getting to the MBeanServer which should be exposed via this manager
     * servlet.
     *
     * <ul>
     <li>Add the Jolokia private MBeanServer</li>
     *   <li>Ask the given server detectors for MBeanServer so that can used container specific lookup
     *       algorithms
     *   <li>Use {@link javax.management.MBeanServerFactory#findMBeanServer(String)} for
     *       registered MBeanServer and take the <b>first</b> one in the returned list
     *   <li>Finally, use the {@link java.lang.management.ManagementFactory#getPlatformMBeanServer()}
     * </ul>
     *
     * @throws IllegalStateException if no MBeanServer could be found.
     * @param pDetectors detectors which might have extra possibilities to add MBeanServers
     */
    private synchronized void init(List<ServerDetector> pDetectors) {

        // Check for JBoss MBeanServer via its utility class
        mBeanServers = new LinkedHashSet<MBeanServerConnection>();

        // Create and add our own JolokiaMBeanServer first
        jolokiaMBeanServer = lookupJolokiaMBeanServer();

        // Let every detector add its own MBeanServer
        for (ServerDetector detector : pDetectors) {
            detector.addMBeanServers(mBeanServers);
        }

        // All MBean Server known by the MBeanServerFactory
        List<MBeanServer> beanServers = MBeanServerFactory.findMBeanServer(null);
        if (beanServers != null) {
            mBeanServers.addAll(beanServers);
        }

        // Last entry is always the platform MBeanServer
        mBeanServers.add(ManagementFactory.getPlatformMBeanServer());

        allMBeanServers = new LinkedHashSet<MBeanServerConnection>();
        if (jolokiaMBeanServer != null) {
            allMBeanServers.add(jolokiaMBeanServer);
        }
        allMBeanServers.addAll(mBeanServers);
    }

    /**
     * Handle a single request
     *
     * @param pRequestHandler the handler which can deal with this request
     * @param pJmxReq the request to execute
     * @return the return value
     *
     * @throws MBeanException
     * @throws ReflectionException
     * @throws AttributeNotFoundException
     * @throws InstanceNotFoundException
     */
    public Object handleRequest(JsonRequestHandler pRequestHandler, JmxRequest pJmxReq)
            throws MBeanException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException {
        AttributeNotFoundException attrException = null;
        InstanceNotFoundException objNotFoundException = null;

        for (MBeanServerConnection conn : getMBeanServers(true)) {
            try {
                return pRequestHandler.handleRequest(conn, pJmxReq);
            } catch (InstanceNotFoundException exp) {
                // Remember exceptions for later use
                objNotFoundException = exp;
            } catch (AttributeNotFoundException exp) {
                attrException = exp;
            } catch (IOException exp) {
                throw new IllegalStateException("I/O Error while dispatching",exp);
            }
        }
        if (attrException != null) {
            throw attrException;
        }
        // Must be there, otherwise we would not have left the loop
        throw objNotFoundException;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<MBeanServerConnection> getMBeanServers(boolean withJolokiaMBeanServer) {
        // Only add the Jolokia MBean server if at least a single MBean is registered there
        if (withJolokiaMBeanServer && jolokiaMBeanServer != null) {
            return allMBeanServers;
        } else {
            return mBeanServers;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected MBeanServerConnection getJolokiaMBeanServer() {
        return jolokiaMBeanServer;
    }

    // ==========================================================================================

    // Check, whether the Jolokia MBean Server is available. If not, register
    // at the Platform MBeanServer delegate to get notified when it gets registered
    private MBeanServer lookupJolokiaMBeanServer() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName holderMBeanName = null;
        try {
            holderMBeanName = new ObjectName("jolokia:type=MBeanServer");
            return (MBeanServer) server.getAttribute(holderMBeanName,"JolokiaMBeanServer");
        } catch (InstanceNotFoundException exp) {
            // Not yet available. Register for when it comes been available.
            MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
            filter.enableObjectName(holderMBeanName);
            try {
                server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this, filter, null);
            } catch (InstanceNotFoundException e) {
                // Will not happen, since a delegate is always created during the creation
                // of an MBeanServer
                throw new IllegalStateException("Internal: Cannot lookup " +
                                                MBeanServerDelegate.DELEGATE_NAME + ": " + e,e);
            }
            return null;
        } catch (JMException e) {
            throw new IllegalStateException("Internal: Cannot get Jolokia MBeanServer via JMX lookup: " + e,e);
        }
    }

    /**
     * Fetch Jolokia MBeanServer when it gets registered
     *
     * @param notification notification emitted
     * @param handback not used here
     */
    public synchronized void handleNotification(Notification notification, Object handback) {
        if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType())) {
            jolokiaMBeanServer = lookupJolokiaMBeanServer();
        } else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(notification.getType())) {
            jolokiaMBeanServer = null;
        }
        allMBeanServers.clear();
        allMBeanServers.add(jolokiaMBeanServer);
        allMBeanServers.addAll(mBeanServers);
    }

    /**
     * Lifecycle method called at the end of life for this object.
     * If registered for Jolokia MBeanServer registration notifications the registration will be removed
     */
    public void destroy() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this);
        } catch (InstanceNotFoundException e) {
            // Will not happen, since a delegate is always created during the creation
            // of an MBeanServer
            throw new IllegalStateException("Internal: Cannot lookup " + MBeanServerDelegate.DELEGATE_NAME + ": " + e,e);
        } catch (ListenerNotFoundException e) {
            // Ignored, but we tried it.
        }

    }

    /**
     * Get a string representation of all servers
     *
     * @return string representation of the servers along with some statistics.
     */
    public String getServersInfo() {
        return ServersInfo.dump(allMBeanServers);
    }
}
