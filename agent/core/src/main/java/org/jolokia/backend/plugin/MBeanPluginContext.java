package org.jolokia.backend.plugin;/*
 * 
 * Copyright 2014 Roland Huss
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

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * A {@link MBeanPlugin}'s context for accessing JMX with all known MBeanServers. Also it allows to register
 * on MBeans for offering own functionality.
 *
 * @author roland
 * @since 12/01/15
 */
public interface MBeanPluginContext extends MBeanServerExecutor {

    /**
     * Register a MBean under a certain name to the platform MBeanServer. No neeed to unregister, the MBean will be
     * automatically unregistered when shutting down the agent. It is recommended to use the domain <code>jolokia</code>
     * and a type <code>type=plugin</code> when registering MBeans.
     *
     * @param pMBean MBean to register
     * @param pOptionalName optional name under which the bean should be registered. If not provided,
     * it depends on whether the MBean to register implements {@link javax.management.MBeanRegistration} or
     * not.
     *
     * @return the name under which the MBean is registered.
     */
    public ObjectName registerMBean(Object pMBean,String ... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException;

}
