package org.jolokia.server.core.service.impl;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.*;

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


/**
 * Handler for finding and merging various MBeanServers locally when used
 * as an agent.
 *
 * @author roland
 * @since Jun 15, 2009
 */
public class MBeanRegistry {

    // Handles remembered for unregistering
    private final List<MBeanHandle> mBeanHandles = new ArrayList<>();

    /**
     * Register a MBean under a certain name to the platform MBeanServer
     *
     * @param pMBean MBean to register
     * @param pOptionalName optional name under which the bean should be registered. If not provided,
     * it depends on whether the MBean to register implements {@link javax.management.MBeanRegistration} or
     * not.
     *
     * @return the name under which the MBean is registered.
     */
    public final ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        synchronized (mBeanHandles) {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            try {
                String name = pOptionalName != null && pOptionalName.length > 0 ? pOptionalName[0] : null;
                ObjectName registeredName = registerMBeanAtServer(server, pMBean, name);
                mBeanHandles.add(new MBeanHandle(server,registeredName));
                return registeredName;
            } catch (RuntimeException exp) {
                throw new IllegalStateException("Could not register " + pMBean + ": " + exp, exp);
            }
        }
    }

    /**
     * Register a MBean at the dedicated server. This method can be overridden if
     * something special registration procedure is required, like for using the
     * specific name for the registration or deligating the naming to MBean to register.
     *
     * @param pServer server an MBean should be registered
     * @param pMBean the MBean to register
     * @param pName an optional name under which the MBean should be registered. Can be null
     * @return the object name of the registered MBean
     * @throws MBeanRegistrationException when registration failed
     * @throws InstanceAlreadyExistsException when there is already MBean with this name
     * @throws NotCompliantMBeanException when the object is not a JMX compliant MBean
     * @throws MalformedObjectNameException if the name is not valid
     */
    private ObjectName registerMBeanAtServer(MBeanServer pServer, Object pMBean, String pName)
            throws MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException, MalformedObjectNameException {
        ObjectName oName = pName != null ? new ObjectName(pName) : null;
        return pServer.registerMBean(pMBean,oName).getObjectName();
    }


    /**
     * Unregister a formerly registered MBean. If the MBean was <em>not</em> registered, the call
     * will be simply ignored
     *
     * @param pObjectName objectname to unregister
     *
     * @throws MBeanRegistrationException if something fails during unregistration
     */
    public void unregisterMBean(ObjectName pObjectName) throws MBeanRegistrationException {
        synchronized(mBeanHandles) {
            MBeanHandle toRemove = null;
            for (MBeanHandle handle : mBeanHandles) {
                if (handle.objectName.equals(pObjectName)) {
                    try {
                        handle.server.unregisterMBean(pObjectName);
                        toRemove = handle;
                        break;
                    } catch (InstanceNotFoundException e) {
                        // Should not occur since we already checked that it exists
                        throw new IllegalStateException("No MBean " + pObjectName + " registered (although still in this registry)",e);
                    }
                }
            }
            if (toRemove != null) {
                mBeanHandles.remove(toRemove);
            }
        }
    }

    /**
     * Unregister all previously registered MBean. This is tried for all previously
     * registered MBeans
     *
     * @throws JMException if an exception occurs during unregistration
     */
    public final void destroy() throws JMException {
        synchronized (mBeanHandles) {
            List<JMException> exceptions = new ArrayList<>();
            List<MBeanHandle> unregistered = new ArrayList<>();
            for (MBeanHandle handle : mBeanHandles) {
                try {
                    unregistered.add(handle);
                    handle.server.unregisterMBean(handle.objectName);
                } catch (InstanceNotFoundException | MBeanRegistrationException e) {
                    exceptions.add(e);
                }
            }
            // Remove all successfully unregistered handles
            mBeanHandles.removeAll(unregistered);

            // Throw error if any exception occurred during unregistration
            if (exceptions.size() == 1) {
                throw exceptions.get(0);
            } else if (exceptions.size() > 1) {
                StringBuilder ret = new StringBuilder();
                for (JMException e : exceptions) {
                    ret.append(e.getMessage()).append(", ");
                }
                throw new JMException(ret.substring(0, ret.length() - 2));
            }
        }
    }

    // ==================================================================================
    // Handle for remembering registered MBeans

    private static final class MBeanHandle {
        private final ObjectName objectName;
        private final MBeanServer server;

        private MBeanHandle(MBeanServer pServer, ObjectName pRegisteredName) {
            server = pServer;
            objectName = pRegisteredName;
        }
    }
}
