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

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.support.jmx.impl.JolokiaMBeanServerHolder;

/**
 * Utility class for looking up the Jolokia-internal MBeanServer which never gets exposed via JSR-160
 *
 * @author roland
 * @since 13.01.13
 */
public final class JolokiaMBeanServerUtil {

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
        MBeanServer jolokiaMBeanServer = null;
        try {
            jolokiaMBeanServer =
                    (MBeanServer) server.getAttribute(JolokiaMBeanServerHolder.MBEAN_SERVER_HOLDER_OBJECTNAME,
                                                      JolokiaMBeanServerHolder.JOLOKIA_MBEAN_SERVER_ATTRIBUTE);
        } catch (InstanceNotFoundException exp) {
            // should be probably locked, but for simplicity reasons and because
            // the probability of a clash is fairly low (can happen only once), it's omitted
            // here. Note, that server.getAttribute() itself is threadsafe.
            Serializer serializer = lookupSerializer();
            if (serializer != null) {
                jolokiaMBeanServer = JolokiaMBeanServerHolder.registerJolokiaMBeanServerHolderMBean(server, serializer);
            }
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

    // If used via this method, only the Jolokia serializer is used. If used via OSGi, any serializer registered
    // as a servie is used.
    private static Serializer lookupSerializer() {
        Class clazz = null;
        try {
            clazz = Class.forName("org.jolokia.service.serializer.JolokiaSerializer");
            return (Serializer) clazz.newInstance();
        } catch (ClassNotFoundException e) {
            // No serializer available
            return null;
        } catch (InstantiationException e) {
            // No serializer available
            return null;
        } catch (IllegalAccessException e) {
            // No serializer available
            return null;
        }
    }
}
