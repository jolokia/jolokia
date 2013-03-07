package org.jolokia.backend;

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

import org.jolokia.backend.executor.AbstractMBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.detector.ServerDetector;
import org.jolokia.handler.JsonRequestHandler;
import org.jolokia.request.JmxRequest;

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

    // Set of MBeanServers found
    private MBeanServers mbeanServers;

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

        // Create the MBeanServerList
        mbeanServers = new MBeanServers(pDetectors,this);

        // Register for registers/deregister of MBean changes in order to update lastUpdateTime
        registerForMBeanNotifications();
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
    public <R extends JmxRequest> Object handleRequest(JsonRequestHandler<R> pRequestHandler, R pJmxReq)
            throws MBeanException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, NotChangedException {
        AttributeNotFoundException attrException = null;
        InstanceNotFoundException objNotFoundException = null;

        for (MBeanServerConnection conn : getMBeanServers()) {
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

    /**
     * Lifecycle method called at the end of life for this object.
     */
    public void destroy() {
        super.destroy();
        mbeanServers.destroy();
    }

    /**
     * Get a string representation of all servers
     *
     * @return string representation of the servers along with some statistics.
     */
    public String getServersInfo() {
        return mbeanServers.dump();
    }

    // ==============================================================================

    /** {@inheritDoc} */
    @Override
    protected Set<MBeanServerConnection> getMBeanServers() {
        return mbeanServers.getMBeanServers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MBeanServerConnection getJolokiaMBeanServer() {
        return mbeanServers.getJolokiaMBeanServer();
    }
}
