package org.jolokia.jmx;

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

import java.lang.management.ManagementFactory;

import javax.management.*;

/**
 * Utility class for looking up the Jolokia-internal MBeanServer which never gets exposed via JSR-160
 *
 * @author roland
 * @since 13.01.13
 */
public final class JolokiaMBeanServerUtil {

    public static final String JOLOKIA_MBEAN_SERVER_ATTRIBUTE = "JolokiaMBeanServer";

    // Only static methods
    private JolokiaMBeanServerUtil() {
    }

    /**
     * Lookup the JolokiaMBean server via a JMX lookup to the Jolokia-internal MBean exposing this MBeanServer
     *
     * @return the Jolokia MBeanServer or null if not yet available present
     */
    public static MBeanServer getJolokiaMBeanServer() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        MBeanServer jolokiaMBeanServer;
        try {
            jolokiaMBeanServer =
                    (MBeanServer) server.getAttribute(createObjectName(JolokiaMBeanServerHolderMBean.OBJECT_NAME),
                                                      JOLOKIA_MBEAN_SERVER_ATTRIBUTE);
        } catch (InstanceNotFoundException exp) {
            // should be probably locked, but for simplicity reasons and because
            // the probability of a clash is fairly low (can happen only once), it's omitted
            // here. Note, that server.getAttribute() itself is threadsafe.
            jolokiaMBeanServer = registerJolokiaMBeanServerHolderMBean(server);
        } catch (JMException e) {
            throw new IllegalStateException("Internal: Cannot get JolokiaMBean server via JMX lookup: " + e,e);
        }
        return jolokiaMBeanServer;
    }


    /**
     * Register an MBean at the JolokiaMBeanServer. This call is directly delegated
     * to the JolokiaMBeanServer
     *
     * @param object object to register
     * @param name object name under which to register the MBean
     * @return the object instance created
     * @throws InstanceAlreadyExistsException
     * @throws MBeanRegistrationException
     * @throws NotCompliantMBeanException
     */
    public static ObjectInstance registerMBean(Object object, ObjectName name) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        return getJolokiaMBeanServer().registerMBean(object, name);
    }


    /**
     * Unregister an MBean at the JolokiaMBeanServer. This call is directly delegated
     * to the JolokiaMBeanServer
     *
     * @param name objectname of the MBean to unregister
     * @throws InstanceNotFoundException
     * @throws MBeanRegistrationException
     */
    public static void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        getJolokiaMBeanServer().unregisterMBean(name);
    }

    // Create a new JolokiaMBeanServerHolder and return the embedded MBeanServer
    // package visible for unit testing
    static MBeanServer registerJolokiaMBeanServerHolderMBean(MBeanServer pServer) {
        JolokiaMBeanServerHolder holder = new JolokiaMBeanServerHolder();
        ObjectName holderName = createObjectName(JolokiaMBeanServerHolderMBean.OBJECT_NAME);
        MBeanServer jolokiaMBeanServer;
        try {
            pServer.registerMBean(holder,holderName);
            jolokiaMBeanServer = holder.getJolokiaMBeanServer();
        } catch (InstanceAlreadyExistsException e) {
            // If the instance already exist, we look it up and fetch the MBeanServerHolder from there.
            // Might happen in race conditions.
            try {
                jolokiaMBeanServer = (MBeanServer) pServer.getAttribute(holderName,JOLOKIA_MBEAN_SERVER_ATTRIBUTE);
            } catch (JMException e1) {
                throw new IllegalStateException("Internal: Cannot get JolokiaMBean server in fallback JMX lookup " +
                                                "while trying to register the holder MBean: " + e1,e1);
            }
        } catch (JMException e) {
            throw new IllegalStateException("Internal: JolokiaMBeanHolder cannot be registered to JMX: " + e,e);
        }
        return jolokiaMBeanServer;
    }

    private static ObjectName createObjectName(String pName) {
        try {
            return new ObjectName(pName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name " + pName,e);
        }
    }
}
