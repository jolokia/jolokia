package org.jolokia.support.jmx;

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

import java.lang.reflect.Proxy;

import javax.management.*;

import org.jolokia.server.core.service.serializer.Serializer;

/**
 * A wrapper class for holding the Jolokia JSR-160 private MBeanServer
 *
 * @author roland
 * @since 14.01.13
 */
public class JolokiaMBeanServerHolder implements JolokiaMBeanServerHolderMBean {

    // The privat Jolokia MBeanServer
    private MBeanServer jolokiaMBeanServer;

    //
    public static final ObjectName MBEAN_SERVER_HOLDER_OBJECTNAME;

    static {
        try {
            MBEAN_SERVER_HOLDER_OBJECTNAME = new ObjectName(OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name " +  OBJECT_NAME,e);
        }
    }
    /**
     * Create a new holder
     */
    JolokiaMBeanServerHolder(MBeanServer pJolokiaMBeanServer) {
        jolokiaMBeanServer = pJolokiaMBeanServer;
    }

    /**
     * Get the managed JolokiaMBeanServer
     *
     * @return the Jolokia MBean Server
     */
    public MBeanServer getJolokiaMBeanServer() {
        return jolokiaMBeanServer;
    }

    /**
     * Register a holder MBean at the platform given MBeanServer (potentially the platform MBeanServer)
     *
     * @param pServer server to register to
     * @param pSerializer serializer to use
     * @return the JolokiaMBeanServer created
     */
    public static MBeanServer registerJolokiaMBeanServerHolderMBean(MBeanServer pServer, Serializer pSerializer) {
        MBeanServer jolokiaMBeanServer;
        ObjectName holderName;
        holderName = MBEAN_SERVER_HOLDER_OBJECTNAME;

        try {
            jolokiaMBeanServer = createJolokiaMBeanServer(pSerializer);
            JolokiaMBeanServerHolder holder = new JolokiaMBeanServerHolder(jolokiaMBeanServer);
            pServer.registerMBean(holder,holderName);
        } catch (InstanceAlreadyExistsException e) {
            // If the instance already exist, we look it up and fetch the MBeanServerHolder from there.
            // Might happen in race conditions.
            try {
                jolokiaMBeanServer = (MBeanServer) pServer.getAttribute(holderName,JOLOKIA_MBEAN_SERVER_ATTRIBUTE);
            } catch (JMException e1) {
                throw new IllegalStateException("Internal: Cannot get JolokiaMBean server in fallback JMX lookup " +
                                                "while trying to register the holder MBean: " + e,e);
            }
        } catch (JMException e) {
            throw new IllegalStateException("Internal: JolokiaMBeanHolder cannot be registered to JMX: " + e,e);
        }
        return jolokiaMBeanServer;
    }

    // Create a proxy for the MBeanServer
    private static MBeanServer createJolokiaMBeanServer(Serializer pSerializer) {
        return (MBeanServer) Proxy.newProxyInstance(JolokiaMBeanServerHolder.class.getClassLoader(), new Class[]{MBeanServer.class},                                                    new JolokiaMBeanServerHandler(pSerializer));
    }

    /**
     * Unregister the holder MBean from the given MBeanServer. If the holder MBean was not registered,
     * nothing happens
     *
     * @param pMBeanServer MBean server to use for unregistering
     */
    public static void unregisterJolokiaMBeanServerHolderMBean(MBeanServer pMBeanServer) {
        try {
            pMBeanServer.unregisterMBean(MBEAN_SERVER_HOLDER_OBJECTNAME);
        } catch (InstanceNotFoundException e) {
            // Silently ignore if not already registered ....
        } catch (MBeanRegistrationException e) {
            throw new IllegalStateException("Cannot unregister " + MBEAN_SERVER_HOLDER_OBJECTNAME + ": " + e,e);
        }
    }
}
