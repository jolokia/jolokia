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

import java.util.*;

import javax.management.*;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.detector.ServerDetector;
import org.jolokia.detector.ServerHandle;

import static org.easymock.EasyMock.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class TestDetector implements ServerDetector {

    private static boolean throwAddException = false;

    private static boolean fallThrough = false;

    private static Exception exps[] = new Exception[] {
            new RuntimeException(),
            new MBeanRegistrationException(new RuntimeException())
    };

    static int instances = 0;
    int nr;

    public TestDetector() {
        nr = instances++;
    }

    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        if (nr == 2) {
            throw new RuntimeException();
        } else if (nr == 3 && !fallThrough) {
            // Break detector chain
            return new ServerHandle(null,null,null,null,null);
        } else {
            return null;
        }
    }

    public void addMBeanServers(Set<MBeanServerConnection> pMBeanServers) {
        if (throwAddException) {
            MBeanServer server = createMock(MBeanServer.class);
            try {
                expect(server.registerMBean(EasyMock.<Object>anyObject(), EasyMock.<ObjectName>anyObject()))
                        .andStubThrow(exps[nr % exps.length]);
                expect(server.queryNames(EasyMock.<ObjectName>anyObject(), (QueryExp) isNull())).andStubAnswer(
                        new IAnswer<Set<ObjectName>>() {
                            public Set<ObjectName> answer() throws Throwable {
                                Object[] args = EasyMock.getCurrentArguments();
                                return new HashSet<ObjectName>(Arrays.asList((ObjectName) args[0]));
                            }
                        });
                expect(server.isRegistered(EasyMock.<ObjectName>anyObject())).andStubReturn(true);
                server.addNotificationListener((ObjectName) anyObject(), (NotificationListener) anyObject(),
                                               (NotificationFilter) anyObject(), anyObject());
                expectLastCall().anyTimes();
                server.removeNotificationListener((ObjectName) anyObject(), (NotificationListener) anyObject());
                expectLastCall().anyTimes();
                replay(server);
                pMBeanServers.add(server);
            } catch (JMException e) {
            }
        }
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
}
