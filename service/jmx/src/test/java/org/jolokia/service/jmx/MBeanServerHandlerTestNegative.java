package org.jolokia.service.jmx;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.*;

import org.jolokia.server.core.service.impl.ClasspathServerDetectorLookup;
import org.jolokia.server.core.service.impl.MBeanRegistry;
import org.jolokia.server.core.util.jmx.DefaultMBeanServerAccess;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 02.09.11
 */
@SuppressWarnings("NewClassNamingConvention")
public class MBeanServerHandlerTestNegative {

    private MBeanRegistry handler;

    @Test
    public void mbeanRegistrationWithFailingTestDetector() throws JMException, IOException {
        TestDetector.setThrowAddException(true);
        // New setup because detection happens at construction time
        init();
        try {
            ObjectName oName = new ObjectName("Bla:type=blub");
            final Set<MBeanServerConnection> connections = new HashSet<>();
            new ClasspathServerDetectorLookup().lookup().forEach(d -> {
                Set<MBeanServerConnection> servers = d.getMBeanServers();
                if (servers != null) {
                    connections.addAll(servers);
                }
            });
            MBeanServerAccess servers = new DefaultMBeanServerAccess(connections);
            final List<Boolean> results = new ArrayList<>();
            servers.each(oName, (pConn, pName) -> results.add(pConn.isRegistered(pName)));
            assertTrue(results.contains(Boolean.TRUE),"MBean not registered");
        } finally {
            TestDetector.setThrowAddException(false);
            handler.destroy();
        }
    }

    // ===================================================================================================

    private void init() {
        TestDetector.reset();
        handler = new MBeanRegistry();
    }

}
