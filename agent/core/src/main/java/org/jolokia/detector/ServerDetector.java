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

import java.lang.instrument.Instrumentation;
import java.util.Set;

import javax.management.MBeanServerConnection;

import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * A detector identifies a specific server. This is typically done by inspecting
 * the runtime environment e.g. for the existence of certain classes. If a detector
 * successfully detect 'its' server, it return a {@link ServerHandle} containing type, version
 * and some optional information. For the early detection of a server by the JVM agent,
 * {@link #jvmAgentStartup(Instrumentation)} and {@link #awaitServerInitialization} can
 * be used. Using these methods is useful in case the server is using its own class
 * loaders to load components used by Jolokia (e.g jmx, Java logging which is
 * indirectly required by the sun.net.httpserver).
 *
 * @author roland
 * @since 05.11.10
 */
public interface ServerDetector {

    /**
     * Detect the server. A {@link ServerHandle} descriptor is returned
     * in case of a successful detection, <code>null</code> otherwise.
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

    /**
     * Notify detector that the JVM is about to start. A detector can, if needed, block and wait for some condition but
     * should ultimatevely return at some point or throw an exception. This notification is executed
     * in a very early stage (premain of the Jolokia JVM agent) before the main class of the Server is executed.
     * @param instrumentation the Instrumentation implementation
     */
    void jvmAgentStartup(Instrumentation instrumentation);
}
