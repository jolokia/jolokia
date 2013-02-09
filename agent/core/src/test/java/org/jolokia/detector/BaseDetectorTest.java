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

import java.util.*;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import org.jolokia.backend.executor.AbstractMBeanServerExecutor;
import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * @author roland
 * @since 17.01.13
 */
public class BaseDetectorTest {
    protected MBeanServerExecutor getMBeanServerManager(final MBeanServer ... pMockServer) {
        return new AbstractMBeanServerExecutor() {
            protected Set<MBeanServerConnection> getMBeanServers() {
                return new LinkedHashSet<MBeanServerConnection>(Arrays.asList(pMockServer));
            }
        };
    }
}
