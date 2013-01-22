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

import java.util.Set;

import javax.management.MBeanServerConnection;

/**
 * A dedicated managed for handling various kinds of MBeanServer related requests.
 *
 * @author roland
 * @since 17.01.13
 */
public interface MBeanServerManager {
    /**
     * Get all active MBeanServer, i.e. excluding the Jolokia MBean Server
     * if it has not MBean attached.
     *
     * @return active MBeanServers
     */
    Set<MBeanServerConnection> getActiveMBeanServers();

    /**
     * Return all MBeanServers except the Jolokia MBeanServer
     * @return all MBeanServer except the Jolokia MBean Server
     */
    Set<MBeanServerConnection> getAllMBeanServers();
}
