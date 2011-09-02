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

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.*;

import org.jolokia.util.LogHandler;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 02.09.11
 */
public class MBeanServerHandlerTest {


    private MBeanServerHandler handler;

    @BeforeMethod
    public void setup() {
        handler = new MBeanServerHandler("qualifier=test",getEmptyLogHandler());
    }

    @Test
    public void mbeanServers() {
        Set<MBeanServer> servers = handler.getMBeanServers();
        assertTrue(servers.size() > 0);
        assertTrue(servers.contains(ManagementFactory.getPlatformMBeanServer()));

        String info = handler.mBeanServersInfo();
        assertTrue(info.contains("Platform MBeanServer"));
        assertTrue(info.contains("type=Memory"));
    }

    @Test
    public void mbeanRegistration() throws JMException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        try {
            handler.init();
            String oName = handler.getObjectName();
        } finally {
            handler.unregisterMBeans();
        }
    }


    // ===================================================================================================


    private LogHandler getEmptyLogHandler() {
        return new LogHandler() {
            public void debug(String message) {
            }

            public void info(String message) {
            }

            public void error(String message, Throwable t) {
            }
        };
    }
}
