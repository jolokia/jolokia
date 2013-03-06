package org.jolokia.jsr160;

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

import java.util.HashSet;
import java.util.Set;

import javax.management.MBeanServerConnection;

import org.jolokia.backend.executor.AbstractMBeanServerExecutor;

/**
 * MBeanServer Manager wrapping a single remote connection
 *
 * @author roland
 * @since 17.01.13
 */
class MBeanServerExecutorRemote extends AbstractMBeanServerExecutor {

    // wrapped collection
    private final Set<MBeanServerConnection> serverConnections;

    /**
     * Constructor for wrapping a remote connection
     * @param pConnection remote connection to wrap
     */
    MBeanServerExecutorRemote(MBeanServerConnection pConnection) {
        serverConnections = new HashSet<MBeanServerConnection>();
        serverConnections.add(pConnection);

        // We dont register for notifications since this a very
        // short lived objects. So, for @link hasUpdatedSince()
        // returns always true here. So be careful for now that
        // there is no optimizations for JSR-160 connections yet.
        // Should be added when pooling is implemented.
    }

    /** {@inheritDoc} */
    protected Set<MBeanServerConnection> getMBeanServers() {
        return serverConnections;
    }
}
