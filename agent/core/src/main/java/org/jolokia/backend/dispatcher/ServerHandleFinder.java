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

package org.jolokia.backend.dispatcher;

import java.util.*;

import javax.management.MBeanServerConnection;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.ConfigKey;
import org.jolokia.detector.*;
import org.jolokia.service.JolokiaContext;

/**
 * @author roland
 * @since 19.06.13
 */
public class ServerHandleFinder {

    private JolokiaContext jolokiaContext;

    SortedSet<ServerDetector> detectors;

    public ServerHandleFinder(JolokiaContext pJolokiaContext) {
        jolokiaContext = pJolokiaContext;
        detectors = pJolokiaContext.getServices(ServerDetector.class);
        detectors.add(new FallbackServerDetector());
    }

    public Set<MBeanServerConnection> getExtraMBeanServers() {
        Set<MBeanServerConnection> ret = new HashSet<MBeanServerConnection>();
        for (ServerDetector detector : detectors) {
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
        handle.setJolokiaId(extractJolokiaId(jolokiaContext));
        return handle;
    }

    /**
     * Extract a unique Id for this agent
     *
     * @param pContext the jolokia context
     * @return the unique Jolokia ID
     */
    private String extractJolokiaId(JolokiaContext pContext) {
        String id = pContext.getConfig(ConfigKey.JOLOKIA_ID);
        if (id != null) {
            return id;
        }
        return Integer.toHexString(hashCode()) + "-unknown";
    }

    // Detect the server by delegating it to a set of predefined detectors-default. These will be created
    // by a lookup mechanism, queried and thrown away after this method
    private ServerHandle detectServers(MBeanServerExecutor pMBeanServerExecutor) {
        // Now detect the server
        for (ServerDetector detector : detectors) {
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
    // Fallback server detector which matches always

    private static class FallbackServerDetector extends AbstractServerDetector {
        public FallbackServerDetector() {
            super(10000);
        }

        /** {@inheritDoc}
         * @param pMBeanServerExecutor*/
        public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
            return ServerHandle.NULL_SERVER_HANDLE;
        }
    }
}
