package org.jolokia.server.detector.jee;

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

import java.util.*;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

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
        super("weblogic",pOrder);
    }

    /** {@inheritDoc}
     * @param pMBeanServerAccess*/
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        String domainConfigMBean = getSingleStringAttribute(pMBeanServerAccess,"*:Name=RuntimeService,*","DomainConfiguration");
        if (domainConfigMBean != null) {
            String version = getSingleStringAttribute(pMBeanServerAccess,domainConfigMBean,"ConfigurationVersion");
            return new WeblogicServerHandle(version);
        }
        return null;
    }

    @Override
    public Set<MBeanServerConnection> getMBeanServers() {

        // Workaround for broken JBoss 4.2.3 which doesn't like JNDI lookups. See #123 for details.
        if (!isJBoss()) {
            InitialContext ctx;
            try {
                // Weblogic stores the MBeanServer in a JNDI context
                ctx = new InitialContext();
                MBeanServerConnection server = (MBeanServerConnection) ctx.lookup("java:comp/env/jmx/runtime");
                if (server != null) {
                    return Collections.singleton(server);
                }
            } catch (NamingException e) {
                // expected and can happen on non-Weblogic platforms
            }
        }
        return null;
    }

    // Workaround for old JBosses.
    private boolean isJBoss() {
        try {
            return Class.forName("org.jboss.mx.util.MBeanServerLocator") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static class WeblogicServerHandle extends DefaultServerHandle {
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
