package org.jolokia.service.jmx.detector;

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

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jolokia.core.service.ServerHandle;
import org.jolokia.core.util.jmx.MBeanServerExecutor;

/**
 * Detector for Weblogic Appservers
 *
 * @author roland
 * @since 05.12.10
 */
public class WeblogicDetector extends AbstractServerDetector {

    /**
     * Create a server detector
     *
     * @param pOrder of the detector (within the list of detectors)
     */
    public WeblogicDetector(int pOrder) {
        super(pOrder);
    }

    /** {@inheritDoc}
     * @param pMBeanServerExecutor*/
    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        String domainConfigMBean = getSingleStringAttribute(pMBeanServerExecutor,"*:Name=RuntimeService,*","DomainConfiguration");
        if (domainConfigMBean != null) {
            String version = getSingleStringAttribute(pMBeanServerExecutor,domainConfigMBean,"ConfigurationVersion");
            return new WeblogicServerHandle(version);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void addMBeanServers(Set<MBeanServerConnection> servers) {
        // Weblogic stores the MBeanServer in a JNDI context
        InitialContext ctx;
        try {
            ctx = new InitialContext();
            MBeanServer server = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
            if (server != null) {
                servers.add(server);
            }
        } catch (NamingException e) {
            // expected and can happen on non-Weblogic platforms
        }
    }

    static class WeblogicServerHandle extends ServerHandle {
        /**
         * Constructor
         *
         * @param version  version used
         */
        public WeblogicServerHandle(String version) {
            super("Oracle", "weblogic", version);
        }
    }
}
