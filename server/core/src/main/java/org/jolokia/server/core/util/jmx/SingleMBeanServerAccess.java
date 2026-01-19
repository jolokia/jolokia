/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.util.jmx;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * A simple executor which uses only a single {@link MBeanServerConnection}. It does not support
 * update change detection.
 *
 * @author roland
 * @since 14.01.14
 */
public class SingleMBeanServerAccess implements MBeanServerAccess {

    private final MBeanServerConnection connection;

    /**
     * Constructor for wrapping a single {@link MBeanServerConnection}
     * @param pConnection remote connection to wrap
     */
    public SingleMBeanServerAccess(MBeanServerConnection pConnection) {
        connection = pConnection;
    }

    @Override
    public Set<MBeanServerConnection> getMBeanServers() {
        return Collections.singleton(connection);
    }

    @Override
    public void each(ObjectName pObjectName, MBeanEachCallback pCallback) throws IOException, JMException {
        boolean pattern = pObjectName != null && pObjectName.isPattern();
        for (ObjectInstance instance : connection.queryMBeans(pObjectName, null)) {
            try {
                pCallback.callback(connection, instance);
            } catch (InstanceNotFoundException exp) {
                // the instance may have been unregistered, so if it's a pattern, move to the next
                // matching instance. Even if not instance is matched at all we don't throw anything
                // suggesting that nothing matched
                if (pattern) {
                    continue;
                }
                // When not a pattern, caller should know that what was expected is not available
                throw exp;
            }
        }
    }

    @Override
    public <R> R call(ObjectName pObjectName, MBeanAction<R> pAction, Object... pExtraArgs) throws IOException, JMException {
        // just execute the action, propagating all the exceptions
        return pAction.execute(connection, pObjectName, pExtraArgs);
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException {
        // just one connection
        return connection.queryNames(pObjectName, null);
    }

    @Override
    public boolean hasMBeansListChangedSince(long pTimestamp) {
        return true;
    }

}
