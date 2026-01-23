/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.service.jmx.handler.list;

import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jolokia.json.JSONArray;
import org.jolokia.server.core.service.api.DataUpdater;

import static org.jolokia.service.jmx.handler.list.DataKeys.INTERFACES;

class ListInterfacesDataUpdater extends DataUpdater {

    protected ListInterfacesDataUpdater() {
        super(100);
    }

    @Override
    public String getKey() {
        return INTERFACES.getKey();
    }

    /**
     * This updater uses special {@code update()} method which accepts {@link MBeanServer} for local access.
     * @param mBeanServer
     * @param pMap
     * @param pObjectName
     * @param pMBeanInfo
     * @param pPathStack
     */
    public void update(MBeanServer mBeanServer, Map<String, Object> pMap, ObjectName pObjectName, MBeanInfo pMBeanInfo, Deque<String> pPathStack) {
        try {
            ClassLoader loader = mBeanServer.getClassLoaderFor(pObjectName);
            String className = pMBeanInfo.getClassName();
            Class<?> clazz = loader == null ? Class.forName(className) : Class.forName(className, false, loader);
            Set<Class<?>> interfaces = new HashSet<>();
            collectInterfaces(clazz, interfaces);
            pMap.put(getKey(), new JSONArray(interfaces.stream().map(Class::getName).collect(Collectors.toList())));
        } catch (InstanceNotFoundException | ClassNotFoundException ignored) {
        }
    }

    private void collectInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
        if (clazz == null) {
            return;
        }
        if (clazz.isInterface()) {
            interfaces.add(clazz);
        } else {
            collectInterfaces(clazz.getSuperclass(), interfaces);
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            collectInterfaces(iface, interfaces);
        }
    }

}
