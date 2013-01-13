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

package org.jolokia.jmx;

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.backend.MBeanServerHandlerMBean;

/**
 * Utility class for looking up the Jolokia-internal MBeanServer which never gets exposed via JSR-160
 *
 * @author roland
 * @since 13.01.13
 */
public class JolokiaMBeanServerUtil {

    // Only static methods
    private JolokiaMBeanServerUtil() { }

    /**
     * Lookup the JolokiaMBean server via a JMX lookup to the Jolokia-internal MBean exposing this MBeanServer
     *
     * @return the Jolokia MBeanServer
     */
    public static MBeanServer getJolokiaMBeanServer() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        MBeanServer jolokiaMBeanServer = null;
        try {
            jolokiaMBeanServer =
                    (MBeanServer) server.getAttribute(new ObjectName(MBeanServerHandlerMBean.OBJECT_NAME),"JolokiaMBeanServer");
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



}
