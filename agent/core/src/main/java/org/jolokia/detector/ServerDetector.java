package org.jolokia.detector;

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

import java.util.Set;

import javax.management.MBeanServerConnection;

import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * A detector identifies a specific server. This is typically done by inspecting
 * the runtime environment e.g. for the existance of certain classes. If a detector
 * successfully detect 'its' server, it return a {@link ServerHandle} containing type, version
 * and some optional information
 * @author roland
 * @since 05.11.10
 */
public interface ServerDetector {

    /**
     * Detect the server. A {@link ServerHandle} descriptor is returned
     * in case of a successful detection, <code>null</code> otherwise.
     *
     *
     *
     *
     * @param pMBeanServerExecutor a set of MBeanServers which can be used for detecting server informations
     * @return the server descriptor or <code>null</code> it this implementation cant detect 'its' server.
     */
    ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor);

    /**
     * Add server specific MBeanServers
     *
     * @param pMBeanServers set to add detected MBeanServers to
     */
    void addMBeanServers(Set<MBeanServerConnection> pMBeanServers);
}
