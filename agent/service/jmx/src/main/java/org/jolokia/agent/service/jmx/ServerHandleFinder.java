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

package org.jolokia.agent.service.jmx;

import java.util.*;

import javax.management.MBeanServerConnection;

import org.jolokia.agent.service.jmx.detector.AbstractServerDetector;
import org.jolokia.backend.ServerHandle;
import org.jolokia.service.detector.ServerDetector;
import org.jolokia.util.jmx.MBeanServerExecutor;
import org.jolokia.service.JolokiaContext;

/**
 * Helper class for detecting a server handle
 *
 * @author roland
 * @since 19.06.13
 */
public class ServerHandleFinder {

    // The context used for detection
    private JolokiaContext jolokiaContext;

    /**
     * Construct this finder
     * @param pJolokiaContext context for reaching the detectors
     */
    public ServerHandleFinder(JolokiaContext pJolokiaContext) {
        jolokiaContext = pJolokiaContext;
    }

    /**
     * Get all extra MBeanServerConnections detected by the ServerDetectors
     * @return
     */
    public Set<MBeanServerConnection> getExtraMBeanServers() {
        Set<MBeanServerConnection> ret = new HashSet<MBeanServerConnection>();
        for (ServerDetector detector : getDetectors()) {
            detector.addMBeanServers(ret);
        }
        return ret;
    }

    /**
     * Initialize the server handle and update the context.
     */
    public ServerHandle detectServerHandle(MBeanServerExecutor pMBeanServerExecutor) {
        ServerHandle handle = detectServers(pMBeanServerExecutor);
        handle.postDetect(pMBeanServerExecutor, jolokiaContext);
        return handle;
    }

    // =====================================================================================
    // Look up detectors as services and add a fallback detector
    private SortedSet<ServerDetector> getDetectors() {
        SortedSet<ServerDetector> detectors = jolokiaContext.getServices(ServerDetector.class);
        detectors.add(new FallbackServerDetector());
        return detectors;
    }

    // Detect the server by delegating it to a set of predefined detectors-default. These will be created
    // by a lookup mechanism, queried and thrown away after this method
    private ServerHandle detectServers(MBeanServerExecutor pMBeanServerExecutor) {
        // Now detect the server
        for (ServerDetector detector : getDetectors()) {
            try {
                ServerHandle info = detector.detect(pMBeanServerExecutor);
                if (info != null) {
                    return info;
                }
            } catch (Exception exp) {
                // We are defensive here and wont stop the servlet because
                // there is a problem with the server detection. A error will be logged
                // nevertheless, though.
                jolokiaContext.error("Error while using detector " + detector.getClass().getSimpleName() + ": " + exp,exp);
            }
        }
        return ServerHandle.NULL_SERVER_HANDLE;
    }

    // ==================================================================================
    // Fallback server detector which matches always and comes last
    private static class FallbackServerDetector extends AbstractServerDetector {
        /**
         * Default constructor with an order of 1000
         */
        public FallbackServerDetector() {
            super(10000);
        }

        /** {@inheritDoc} */
        public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
            return ServerHandle.NULL_SERVER_HANDLE;
        }
    }
}
