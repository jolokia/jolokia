package org.jolokia.backend;

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
import java.util.List;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.detector.ServerHandle;
import org.jolokia.util.LogHandler;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class MBeanServerHandlerTestNegative {

    private MBeanServerHandler handler;

    @Test
    public void mbeanRegistrationWithFailingTestDetector() throws JMException, IOException {
        TestDetector.setThrowAddException(true);
        // New setup because detection happens at construction time
        init();
        try {
            ObjectName oName = new ObjectName(handler.getObjectName());
            MBeanServerExecutor servers = handler.getMBeanServerManager();
            final List<Boolean> results = new ArrayList<Boolean>();
            servers.each(oName, new MBeanServerExecutor.MBeanEachCallback() {
                public void callback(MBeanServerConnection pConn, ObjectName pName)
                        throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                    results.add(pConn.isRegistered(pName));
                }
            });
            assertTrue(results.contains(Boolean.TRUE),"MBean not registered");
        } finally {
            TestDetector.setThrowAddException(false);
            handler.destroy();
        }
    }

    @Test
    public void fallThrough() throws JMException {
        TestDetector.setFallThrough(true);
        init();
        try {
            ServerHandle handle = handler.getServerHandle();
            assertNull(handle.getProduct());
        } finally {
            TestDetector.setFallThrough(false);
            handler.destroy();
        }
    }

    // ===================================================================================================

    private void init() throws MalformedObjectNameException {
        TestDetector.reset();
        Configuration config = new Configuration(ConfigKey.MBEAN_QUALIFIER,"qualifier=test");
        handler = new MBeanServerHandler(config, new LogHandler() {
            public void debug(String message) {
            }

            public void info(String message) {
            }

            public void error(String message, Throwable t) {
            }
        });
    }

}
