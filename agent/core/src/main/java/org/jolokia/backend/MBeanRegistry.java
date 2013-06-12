package org.jolokia.backend;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.*;

import org.jolokia.detector.ServerHandle;
import org.jolokia.service.JolokiaContext;

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

    // Jolokia context to use
    private final JolokiaContext jolokiaContext;

    // Handles remembered for unregistering
    private final List<MBeanHandle> mBeanHandles = new ArrayList<MBeanHandle>();

    /**
     * Create a new MBeanServer handler who is responsible for managing multiple intra VM {@link MBeanServer} at once
     * An optional qualifier used for registering this object as an MBean is taken from the given configuration as well
     *
     * @partam pCtx context from where to get configuration information
     */
    public MBeanRegistry(JolokiaContext pCtx) {
        // A qualifier, if given, is used to add the MBean Name of this MBean
        jolokiaContext = pCtx;
    }

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
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        synchronized (mBeanHandles) {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            try {
                String name = pOptionalName != null && pOptionalName.length > 0 ? pOptionalName[0] : null;
                ServerHandle serverHandle = jolokiaContext.getServerHandle();
                ObjectName registeredName = serverHandle.registerMBeanAtServer(server, pMBean, name);
                mBeanHandles.add(new MBeanHandle(server,registeredName));
                return registeredName;
            } catch (RuntimeException exp) {
                throw new IllegalStateException("Could not register " + pMBean + ": " + exp, exp);
            } catch (MBeanRegistrationException exp) {
                throw new IllegalStateException("Could not register " + pMBean + ": " + exp, exp);
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
            List<JMException> exceptions = new ArrayList<JMException>();
            List<MBeanHandle> unregistered = new ArrayList<MBeanHandle>();
            for (MBeanHandle handle : mBeanHandles) {
                try {
                    unregistered.add(handle);
                    handle.server.unregisterMBean(handle.objectName);
                } catch (InstanceNotFoundException e) {
                    exceptions.add(e);
                } catch (MBeanRegistrationException e) {
                    exceptions.add(e);
                }
            }
            // Remove all successfully unregistered handles
            mBeanHandles.removeAll(unregistered);

            // Throw error if any exception ocured during unregistration
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
        private ObjectName objectName;
        private MBeanServer server;

        private MBeanHandle(MBeanServer pServer, ObjectName pRegisteredName) {
            server = pServer;
            objectName = pRegisteredName;
        }
    }
}
