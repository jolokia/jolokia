package org.jolokia.service.jmx;

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

import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.easymock.EasyMock;
import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;

/**
 * @author roland
 * @since 02.09.11
 */
public class TestDetector implements ServerDetector {

    private static boolean throwAddException = false;

    private static boolean fallThrough = false;

    private static final Exception[] exps = new Exception[] {
            new RuntimeException(),
            new MBeanRegistrationException(new RuntimeException())
    };

    static int instances = 0;
    private final int order;
    int nr;

    public TestDetector(int pOrder) {
        order = pOrder;
        nr = instances++;
    }

    public String getName() {
        return "test";
    }

    public void init(Map<String, Object> pConfig) {

    }

    public void init(JolokiaContext pJolokiaContext) {

    }

    @Override
    public boolean isEnabled(JolokiaContext pContext) {
        return true;
    }

    public void destroy() {

    }

    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        if (nr == 2) {
            throw new RuntimeException();
        } else if (nr == 3 && !fallThrough) {
            // Break detector chain
            return ServerHandle.NULL_SERVER_HANDLE;
        } else {
            return null;
        }
    }

    public RequestInterceptor getRequestInterceptor(MBeanServerAccess pMBeanServerAccess) {
        return null;
    }

    public int getOrder() {
        return order;
    }

    public Set<MBeanServerConnection> getMBeanServers() {
        if (throwAddException) {
            MBeanServer server = createMock(MBeanServer.class);
            try {
                expect(server.registerMBean(EasyMock.anyObject(), EasyMock.anyObject()))
                        .andStubThrow(exps[nr % exps.length]);
                expect(server.queryMBeans(EasyMock.anyObject(), isNull())).andStubAnswer(
                        () -> {
                            Object[] args = EasyMock.getCurrentArguments();
                            return new HashSet<>(Collections.singletonList(new ObjectInstance((ObjectName) args[0], null)));
                        });
                expect(server.isRegistered(EasyMock.anyObject())).andStubReturn(true);
                server.addNotificationListener(anyObject(), (NotificationListener) anyObject(),
                        anyObject(), anyObject());
                expectLastCall().anyTimes();
                server.removeNotificationListener(anyObject(), (NotificationListener) anyObject());
                expectLastCall().anyTimes();
                replay(server);
                return Collections.singleton(server);
            } catch (JMException ignored) {
            }
        }
        return null;
    }

    public ClassLoader jvmAgentStartup(Instrumentation instrumentation) {
        return getClass().getClassLoader();
    }

    public ContainerLocator getContainerLocator(LogHandler logHandler) {
        return null;
    }

    public static void setThrowAddException(boolean b) {
        throwAddException = b;
    }

    public static void reset() {
        instances = 0;
    }

    public static void setFallThrough(boolean b) {
        fallThrough = b;
    }

    public int compareTo(ServerDetector o) {
        return getOrder() - o.getOrder();
    }
}
