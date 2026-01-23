/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.service.jmx.handler.list;

import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jolokia.service.jmx.api.CacheKeyProvider;

public class JDKManagementCacheKeyProvider extends CacheKeyProvider {

    public JDKManagementCacheKeyProvider(int pOrderId) {
        super(pOrderId);
    }

    @Override
    public String determineKey(ObjectInstance objectInstance) {
        ObjectName name = objectInstance.getObjectName();

        // we have these "platform managed objects" available in public packages and which are MXBeans:
        //  - java.lang.management.BufferPoolMXBean
        //  - java.lang.management.ClassLoadingMXBean
        //  - java.lang.management.CompilationMXBean
        //  - java.lang.management.GarbageCollectorMXBean
        //  - java.lang.management.MemoryManagerMXBean
        //  - java.lang.management.MemoryMXBean
        //  - java.lang.management.MemoryPoolMXBean
        //  - java.lang.management.OperatingSystemMXBean
        //  - java.lang.management.PlatformLoggingMXBean
        //  - java.lang.management.RuntimeMXBean
        //  - java.lang.management.ThreadMXBean
        //  - jdk.management.jfr.FlightRecorderMXBean
        // only 4 of these have multiple MXBeans registered, so no need to cache others

        if ("java.lang".equals(name.getDomain()) && "GarbageCollector".equals(name.getKeyProperty("type"))) {
            // interface is com.sun.management.GarbageCollectorMXBean
            // class is com.sun.management.internal.GarbageCollectorExtImpl
            return "java.lang:GarbageCollector";
        }
        if ("java.lang".equals(name.getDomain()) && "MemoryPool".equals(name.getKeyProperty("type"))) {
            // interface is java.lang.management.MemoryPoolMXBean
            // class is sun.management.MemoryPoolImpl
            return "java.lang:MemoryPool";
        }
        if ("java.lang".equals(name.getDomain()) && "MemoryManager".equals(name.getKeyProperty("type"))) {
            // interface is java.lang.management.MemoryManagerMXBean
            // class is sun.management.MemoryManagerImpl
            return "java.lang:MemoryManager";
        }
        if ("java.nio".equals(name.getDomain()) && "BufferPool".equals(name.getKeyProperty("type"))) {
            // interface is java.lang.management.BufferPoolMXBean
            // class is sun.management.ManagementFactoryHelper$1
            return "java.nio:BufferPool";
        }

        return null;
    }

}
