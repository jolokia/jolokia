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

import java.security.Principal;
import java.util.*;
import javax.security.auth.Subject;

import org.jolokia.server.core.auth.JolokiaAgentPrincipal;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.util.DebugStore;
import org.jolokia.core.util.LocalServiceFactory;
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
        manager.addServices(new ClasspathServiceCreator(LocalServiceFactory.class.getClassLoader(), "testServices"));
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
        manager.addServices(new ClasspathServiceCreator(LocalServiceFactory.class.getClassLoader(), "testServices"));
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
        manager.addServices(new ClasspathServiceCreator(LocalServiceFactory.class.getClassLoader(), "testServices"));
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
        manager.addServices(new ClasspathServiceCreator(LocalServiceFactory.class.getClassLoader(), "testServices"));
        JolokiaContext context;
        try {
            context = manager.start();
            SortedSet<JolokiaService.Init> set = context.getServices(JolokiaService.Init.class);
            assertEquals(set.size(), 2);
        } finally {
            manager.stop();
        }
    }

    @Test
    public void jaasDefaultPrincipalsTest() {
        StaticConfiguration config = new StaticConfiguration(
                ConfigKey.AGENT_ID, "test"
        );

        final Subject[] subject = new Subject[] { null };

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
                .createJolokiaServiceManager(config, null, null, null);
        manager.addService(new AbstractJolokiaService<>(JolokiaService.Init.class, 1) {
            @Override
            public void init(JolokiaContext pJolokiaContext) {
                try {
                    pJolokiaContext.getSubjectAccess().callAsJolokia(() -> {
                        subject[0] = pJolokiaContext.getSubjectAccess().currentSubject();
                        return null;
                    });
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        });
        JolokiaContext context;
        try {
            manager.start();

            assertNotNull(subject[0]);

            Set<JolokiaAgentPrincipal> roles = subject[0].getPrincipals(JolokiaAgentPrincipal.class);
            assertEquals(roles.size(), 1);
            assertEquals(roles.stream().filter(r -> r.getName().equals("jolokia")).count(), 1);
        } finally {
            manager.stop();
        }
    }

    @Test
    public void jaasPrincipalsTest() {
        StaticConfiguration config = new StaticConfiguration(
                ConfigKey.AGENT_ID, "test",
                ConfigKey.USER_PRINCIPAL_CLASS, UserPrincipal.class.getName(),
                ConfigKey.USER_PRINCIPAL_NAME, " Jolokia\t",
                ConfigKey.ROLE_PRINCIPAL_CLASS, RolePrincipal.class.getName(),
                ConfigKey.ROLE_PRINCIPAL_NAMES, "r1, ,   r2"
        );

        final Subject[] subject = new Subject[] { null };

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
                .createJolokiaServiceManager(config, null, null, null);
        manager.addService(new AbstractJolokiaService<>(JolokiaService.Init.class, 1) {
            @Override
            public void init(JolokiaContext pJolokiaContext) {
                try {
                    pJolokiaContext.getSubjectAccess().callAsJolokia(() -> {
                        subject[0] = pJolokiaContext.getSubjectAccess().currentSubject();
                        return null;
                    });
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        });
        JolokiaContext context;
        try {
            manager.start();

            assertNotNull(subject[0]);
            Principal user = subject[0].getPrincipals(UserPrincipal.class).iterator().next();
            assertEquals(user.getName(), "Jolokia");

            Set<RolePrincipal> roles = subject[0].getPrincipals(RolePrincipal.class);
            assertEquals(roles.size(), 2);
            assertEquals(roles.stream().filter(r -> r.getName().equals("r1")).count(), 1);
            assertEquals(roles.stream().filter(r -> r.getName().equals("r2")).count(), 1);
        } finally {
            manager.stop();
        }
    }

    @Test
    public void jaasDisabledRolePrincipalsTest() {
        StaticConfiguration config = new StaticConfiguration(
                ConfigKey.AGENT_ID, "test",
                ConfigKey.ROLE_PRINCIPAL_CLASS, "",
                ConfigKey.ROLE_PRINCIPAL_NAMES, "r1, ,   r2"
        );

        final Subject[] subject = new Subject[] { null };

        JolokiaServiceManager manager = JolokiaServiceManagerFactory
                .createJolokiaServiceManager(config, null, null, null);
        manager.addService(new AbstractJolokiaService<>(JolokiaService.Init.class, 1) {
            @Override
            public void init(JolokiaContext pJolokiaContext) {
                try {
                    pJolokiaContext.getSubjectAccess().callAsJolokia(() -> {
                        subject[0] = pJolokiaContext.getSubjectAccess().currentSubject();
                        return null;
                    });
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        });
        JolokiaContext context;
        try {
            manager.start();

            assertNotNull(subject[0]);
            assertEquals(subject[0].getPrincipals().size(), 0);
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

    public static class UserPrincipal implements Principal {
        public String userName;
        public UserPrincipal(String userName) {
            this.userName = userName;
        }

        @Override
        public String getName() {
            return userName;
        }
    }

    public static class RolePrincipal implements Principal {
        public String roleName;
        public RolePrincipal(String userName) {
            this.roleName = userName;
        }

        @Override
        public String getName() {
            return roleName;
        }
    }

}
