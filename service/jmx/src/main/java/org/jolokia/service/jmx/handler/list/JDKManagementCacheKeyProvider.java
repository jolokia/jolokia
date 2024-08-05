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
        if ("java.lang".equals(name.getDomain()) && "MemoryPool".equals(name.getKeyProperty("type"))) {
            // class is sun.management.MemoryPoolImpl
            return "java.lang:MemoryPool";
        }
        if ("java.lang".equals(name.getDomain()) && "MemoryManager".equals(name.getKeyProperty("type"))) {
            // class is sun.management.MemoryManagerImpl
            return "java.lang:MemoryManager";
        }
        if ("java.nio".equals(name.getDomain()) && "BufferPool".equals(name.getKeyProperty("type"))) {
            // class is sun.management.ManagementFactoryHelper$1
            return "java.nio:BufferPool";
        }

        return null;
    }

}
