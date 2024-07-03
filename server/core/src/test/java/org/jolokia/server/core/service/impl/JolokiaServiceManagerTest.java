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
package org.jolokia.server.core.service.impl;

import java.util.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.util.DebugStore;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class JolokiaServiceManagerTest {

    @Test
    public void justStart() {
        StaticConfiguration config = new StaticConfiguration(
            ConfigKey.AGENT_ID, "test"
        );

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
            .createJolokiaServiceManager(config, null, null, null);
        JolokiaContext context;
        try {
            context = manager.start();
            @SuppressWarnings("rawtypes")
            SortedSet<JolokiaService> set = context.getServices(JolokiaService.class);
            assertEquals(set.size(), 0);

            // added statically
            assertNotNull(context.getService(RequestHandler.class));
            // added statically (DebugStore)
            assertEquals(context.getServices(JolokiaService.Init.class).size(), 1);
        } finally {
            manager.stop();
        }
    }

    @Test
    public void customServices() {
        StaticConfiguration config = new StaticConfiguration(
            ConfigKey.AGENT_ID, "test"
        );

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
            .createJolokiaServiceManager(config, null, null, null);
        manager.addServices(new ClasspathServiceCreator("testServices"));
        JolokiaContext context;
        try {
            context = manager.start();
            SortedSet<JolokiaService.Init> set = context.getServices(JolokiaService.Init.class);
            // includes DebugStore
            assertEquals(set.size(), 4);
            // added statically
            assertNotNull(context.getService(RequestHandler.class));
        } finally {
            manager.stop();
        }
    }

    @Test
    public void excludedServices() {
        StaticConfiguration config = new StaticConfiguration(
            ConfigKey.AGENT_ID, "test",
            ConfigKey.DISABLED_SERVICES, DebugStore.class.getName() + ", " + Service2.class.getName()
        );

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
            .createJolokiaServiceManager(config, null, null, null);
        manager.addServices(new ClasspathServiceCreator("testServices"));
        JolokiaContext context;
        try {
            context = manager.start();
            SortedSet<JolokiaService.Init> set = context.getServices(JolokiaService.Init.class);
            assertEquals(set.size(), 2);
            // added statically
            assertNotNull(context.getService(RequestHandler.class));
        } finally {
            manager.stop();
        }
    }

    @Test
    public void includedServices() {
        StaticConfiguration config = new StaticConfiguration(
            ConfigKey.AGENT_ID, "test",
            ConfigKey.ENABLED_SERVICES, DebugStore.class.getName()
                + ", " + Service1.class.getName()
                + ", " + Service2.class.getName()
        );

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
            .createJolokiaServiceManager(config, null, null, null);
        manager.addServices(new ClasspathServiceCreator("testServices"));
        JolokiaContext context;
        try {
            context = manager.start();
            SortedSet<JolokiaService.Init> set = context.getServices(JolokiaService.Init.class);
            assertEquals(set.size(), 3);
            // added statically, but removed
            assertNull(context.getService(RequestHandler.class));
        } finally {
            manager.stop();
        }
    }

    @Test
    public void includedAndExcludedServices() {
        StaticConfiguration config = new StaticConfiguration(
            ConfigKey.AGENT_ID, "test",
            ConfigKey.DISABLED_SERVICES, DebugStore.class.getName() + ", " + Service2.class.getName(),
            ConfigKey.ENABLED_SERVICES, DebugStore.class.getName()
                + ", " + Service1.class.getName()
                + ", " + Service2.class.getName()
        );

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
            .createJolokiaServiceManager(config, null, null, null);
        manager.addServices(new ClasspathServiceCreator("testServices"));
        JolokiaContext context;
        try {
            context = manager.start();
            SortedSet<JolokiaService.Init> set = context.getServices(JolokiaService.Init.class);
            assertEquals(set.size(), 2);
        } finally {
            manager.stop();
        }
    }

    public static class Service1 extends AbstractJolokiaService<JolokiaService.Init> {
        protected Service1(Class<JolokiaService.Init> pType, int pOrderId) {
            super(pType, pOrderId);
        }

        public Service1() {
            super(JolokiaService.Init.class, 0);
        }
    }

    public static class Service2 extends AbstractJolokiaService<JolokiaService.Init> {
        protected Service2(Class<JolokiaService.Init> pType, int pOrderId) {
            super(pType, pOrderId);
        }

        public Service2() {
            super(JolokiaService.Init.class, 0);
        }
    }

    public static class Service3 extends AbstractJolokiaService<JolokiaService.Init> {
        protected Service3(Class<JolokiaService.Init> pType, int pOrderId) {
            super(pType, pOrderId);
        }

        public Service3() {
            super(JolokiaService.Init.class, 0);
        }
    }

}
