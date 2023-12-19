package org.jolokia.server.core.detector;

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
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;

import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * A detector identifies a specific server. This is typically done by inspecting
 * the runtime environment e.g. for the existence of certain classes. If a detector
 * successfully detect 'its' server, it return a {@link ServerHandle} containing type, version
 * and some optional information. For the early detection of a server by the JVM agent,
 * {@link #jvmAgentStartup(Instrumentation)} can be used. Using these methods is useful in case
 * the server is using its own class loaders to load components used by Jolokia (e.g jmx, Java logging
 * which is indirectly required by the sun.net.httpserver).
 *
 * @author roland
 * @since 05.11.10
 */
public interface ServerDetector extends JolokiaService<ServerDetector>, Comparable<ServerDetector> {

    @Override
    default Class<ServerDetector> getType() {
        return ServerDetector.class;
    }

    /**
     * Name of the detector which should reflect the server to be detected
     * @return server name
     */
    String getName();

    /**
     * Initialize with detector specific configuration.
     *
     * @param pConfig configuration which is key based map. Can be null if there is no configuration at all.
     */
    void init(Map<String,Object> pConfig);

    /**
     * Detect the server. A {@link ServerHandle} descriptor is returned
     * in case of a successful detection, <code>null</code> otherwise.
     *
     * @param pMBeanServerAccess a set of MBeanServers which can be used for detecting server informations
     * @return the server descriptor or <code>null</code> it this implementation cant detect 'its' server.
     */
    ServerHandle detect(MBeanServerAccess pMBeanServerAccess);

    /**
     * Add MBeanServers dedicated specifically on the identified platform. This method must be overridden
     * by any platform wanting to add MBeanServers. By default this method does nothing.
     *
     * @return mbean servers which are specific for this server or null if none apply
     */
    Set<MBeanServerConnection> getMBeanServers();

    /**
     * Get an request interceptor to add for dealing with server specific workarounds or behaviour
     *
     * @param pMBeanServerAccess  for accessing the JMX subsystem
     * @return a request interceptor to apply for this server or <code>null</code> if none is necessary.
     */
    RequestInterceptor getRequestInterceptor(MBeanServerAccess pMBeanServerAccess);

    /**
     * Order of the service. The higher the number, the later in the list of services this service appears.
     * Default order is 100.
     *
     * @return the order of this service
     */
    int getOrder();

    /**
     * Notify detector that the JVM is about to start. A detector can, if needed, block and wait for some condition but
     * should ultimatevely return at some point or throw an exception. This notification is executed
     * in a very early stage (premain of the Jolokia JVM agent) before the main class of the Server is executed.
     * @param instrumentation the Instrumentation implementation
     */
    void jvmAgentStartup(Instrumentation instrumentation);

    // ==================================================================================

    // Fallback server detector which matches always and comes last
    ServerDetector FALLBACK = new FallbackServerDetector();
}
