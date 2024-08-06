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
package org.jolokia.server.core.util.jmx;

import java.lang.management.ManagementFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.testng.annotations.Test;

public class JmxQueriesTest {

    @Test
    public void fastReqistrationAndQuery() throws NoSuchAlgorithmException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, InterruptedException {
        // This tests is used to check the impact of fast (un)registration of MBeans and querying them at the same time.
        // I checked how JConsole deals with that and:
        //  - sun.tools.jconsole.MBeansTab.buildMBeanServerView() registers a notification listener
        //    for MBeanServerDelegate.DELEGATE_NAME with listener being sun.tools.jconsole.MBeansTab
        //  - sun.tools.jconsole.MBeansTab.handleNotification() calls typical UI java.awt.EventQueue.invokeLater()
        //    with runnable that simply calls one of:
        //     - sun.tools.jconsole.inspector.XTree.addMBeanToView(javax.management.ObjectName)
        //     - sun.tools.jconsole.inspector.XTree.removeMBeanFromView(javax.management.ObjectName)
        //  - there's a cache involved: sun.tools.jconsole.inspector.XTree.nodes
        //  - the cache optimizes access to javax.swing.tree.DefaultTreeModel

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        AtomicBoolean stop = new AtomicBoolean(false);
        String[] registered = new String[10_000];
        String[] unregistered = new String[10_000];
        AtomicInteger regCount = new AtomicInteger(0);

        for (int i = 0; i < 10_000; i++) {
            // javax.management.ObjectName.compareTo() shows that "type" property is kind of special (not much,
            // but still)
            unregistered[i] = String.format("jolokia:type=Flood,size=%05d", i);
        }

        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        // a thread that quickly registers random MBeans
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 0; i < 1000; i++) {
                        int idx = random.nextInt(10_000);
                        if (unregistered[idx] != null) {
                            try {
                                server.registerMBean(new MyResource(), newObjectName(unregistered[idx]));
                                regCount.incrementAndGet();
                                registered[idx] = unregistered[idx];
                                unregistered[idx] = null;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (regCount.get() > 9_000) {
                        for (int i = 0; i < 400; i++) {
                            int idx = random.nextInt(10_000);
                            if (registered[idx] != null) {
                                try {
                                    server.unregisterMBean(newObjectName(registered[idx]));
                                    regCount.decrementAndGet();
                                    unregistered[idx] = registered[idx];
                                    registered[idx] = null;
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        AtomicInteger regNotifications = new AtomicInteger(0);
        AtomicInteger unregNotifications = new AtomicInteger(0);
        server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                if (!(notification instanceof MBeanServerNotification)) {
                    return;
                }
                MBeanServerNotification notif = (MBeanServerNotification) notification;
                if (notif.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
//                    System.out.println("Registered " + notif.getMBeanName() + " (" + notif.getSource() + ")");
                    regNotifications.incrementAndGet();
                } else if (notif.getType().equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
                    unregNotifications.incrementAndGet();
                }
            }
        }, null, null);
        thread.start();

        System.out.println("PID: " + server.getAttribute(new ObjectName("java.lang:type=Runtime"), "Name"));

        // These two invocations are synchronized over a Lock:
        //  - com.sun.jmx.mbeanserver.Repository.addMBean()
        //  - com.sun.jmx.mbeanserver.Repository.query()

        ObjectName pattern = newObjectName("jolokia:type=Flood,*");
        Thread.sleep(1000);
        for (int i = 0; i < 100; i++) {
            Set<ObjectName> names = server.queryNames(pattern, null);
            Set<ObjectInstance> instances = server.queryMBeans(pattern, null);
//            System.out.println("found " + names.size());
//            System.out.println("found " + instances.size());
        }

        System.out.println();
    }

    private ObjectName newObjectName(String s) {
        try {
            return new ObjectName(s);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }


    public interface MyResourceMBean {
        String getInfo();
    }

    public static class MyResource implements MyResourceMBean {

        @Override
        public String getInfo() {
            return "Hello";
        }
    }

}
