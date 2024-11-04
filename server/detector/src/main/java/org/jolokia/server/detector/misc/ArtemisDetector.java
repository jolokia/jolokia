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
package org.jolokia.server.detector.misc;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.MBeanServer;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.detector.jee.AbstractServerDetector;

/**
 * Detects whether we run within <a href="https://activemq.apache.org/components/artemis/">ActiveMQ Artemis</a>
 * server - as WAR or JVM agent.
 */
public class ArtemisDetector extends AbstractServerDetector {

    private static final String jmxBuilderClass = "org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder";
    private static final String jmxHandlerClass = "org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler";
    private static final String artemisBrokerClass = "org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl";

    private static final int ARTEMIS_DETECT_TIMEOUT = 2 * 60 * 1000;
    private static final int ARTEMIS_DETECT_INTERVAL = 200;
    private static final int ARTEMIS_DETECT_FINAL_DELAY = 200;

//    // see org.apache.activemq.artemis.spi.core.security.jaas.HttpServerAuthenticator
//    // https://github.com/apache/activemq-artemis/blob/2.38.0/artemis-server/src/main/java/org/apache/activemq/artemis/spi/core/security/jaas/HttpServerAuthenticator.java#L52
//    private static final String REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME = "httpServerAuthenticator.requestSubjectAttribute";
//    private static final String DEFAULT_SUBJECT_ATTRIBUTE = "org.apache.activemq.artemis.jaasSubject";
//    private final String subjectRequestAttribute = System.getProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME, DEFAULT_SUBJECT_ATTRIBUTE);

    /** Artemis version detected from Maven metadata */
    private String artemisVersion = null;

    /**
     * An instance of Artemis broker. It can only be accessed by this detector if Artemis is configured
     * with {@code javax.management.builder.initial} property set to such builder that wraps {@link MBeanServer}
     * using an {@link java.lang.reflect.InvocationHandler} =
     * {@code org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler}. This invocation
     * handler has special logic to obtain an instance to Artemis broker from first {@link MBeanServer#registerMBean}
     * call that carries the instance (which is
     * {@code org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl}).
     */
    private Object artemisInstance = null;

    private volatile boolean jvmAgentInitialization = false;

    public ArtemisDetector(int pOrder) {
        super("Artemis", pOrder);

        URL artemisMavenPropertiesLocation = getClass().getClassLoader().getResource("META-INF/maven/org.apache.activemq/artemis-boot/pom.properties");
        if (artemisMavenPropertiesLocation != null) {
            try (InputStream is = artemisMavenPropertiesLocation.openStream()) {
                Properties props = new Properties();
                props.load(is);
                artemisVersion = props.getProperty("version");
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    protected int getDetectionTimeout() {
        return ARTEMIS_DETECT_TIMEOUT;
    }

    @Override
    protected int getDetectionInterval() {
        return ARTEMIS_DETECT_INTERVAL;
    }

    @Override
    protected int getDetectionFinalDelay() {
        return ARTEMIS_DETECT_FINAL_DELAY;
    }

    // The system properties that can help us detect Artemis are:
    // "artemis.home" -> "/path/to/artemis"
    // "artemis.instance" -> "/path/to/artemis/broker1"
    // "artemis.instance.etc" -> "/path/to/artemis/broker1/etc"
    // "data.dir" -> "/path/to/artemis/broker1/data"
    // "java.class.path" -> "/path/to/artemis/lib/artemis-boot.jar"
    // "javax.management.builder.initial" -> "org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder"
    // "sun.java.command" -> "org.apache.activemq.artemis.boot.Artemis run"

    // "javax.management.builder.initial" is set only when RBAC security is enabled like in
    // https://activemq.apache.org/components/artemis/documentation/latest/management.html#jmx-authorization-in-broker-xml

    @Override
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        return artemisVersion != null ? new DefaultServerHandle("Apache", getName(), artemisVersion) : null;
    }

    @Override
    public ClassLoader jvmAgentStartup(Instrumentation instrumentation) {
        if (artemisVersion == null) {
            return null;
        }

        // -Djavax.management.builder.initial
        // https://activemq.apache.org/components/artemis/documentation/latest/management.html#jmx-authorization-in-broker-xml
        // com.sun.jmx.defaults.JmxProperties.JMX_INITIAL_BUILDER
        boolean artemisBuilderUsed = jmxBuilderClass.equals(System.getProperty("javax.management.builder.initial"));

        AtomicBoolean brokerClassLoaded = new AtomicBoolean(false);
        // if Artemis sets own builder, we assume it'll get a reference to the broker
        AtomicBoolean brokerAvailable = new AtomicBoolean(false);
        // however if (which shouldn't be possible) MBeanServer is not a proxy or the proxy doesn't
        // contain known field to store broker instance, we will skip the detection
        AtomicBoolean brokerDetectionPossible = new AtomicBoolean(true);

        AtomicReference<ClassLoader> classloader = new AtomicReference<>();

        // we need to wait for two things
        //  - a classloader with jars from $ARTEMIS_HOME/lib + $ARTEMIS_INSTANCE/lib
        //  - if Artemis sets javax.management.builder.initial, we need to grab the invocation handler
        //    and broker instance it contains
        activeWait(instrumentation,
            () -> {
                boolean loaded = brokerClassLoaded.get();
                if (!loaded) {
                    Class<?> c = getClassLoaded(artemisBrokerClass, instrumentation);
                    if (c != null) {
                        loaded = true;
                        brokerClassLoaded.set(true);
                        classloader.set(c.getClassLoader());
                    }
                }
                if (loaded && brokerDetectionPossible.get() && !brokerAvailable.get()) {
                    try {
                        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                        // no exception thrown - we can try getting tbe broker instance if MBeanServer
                        // is a proxy and its invocation handler has known field to store the broker
                        if (!Proxy.isProxyClass(server.getClass())) {
                            brokerDetectionPossible.set(false);
                        } else {
                            InvocationHandler handler = Proxy.getInvocationHandler(server);
                            if (!jmxHandlerClass.equals(handler.getClass().getName())) {
                                brokerDetectionPossible.set(false);
                            } else {
                                Field serverField;
                                try {
                                    serverField = handler.getClass().getDeclaredField("activeMQServer");
                                    serverField.setAccessible(true);
                                    Object brokerInstance = serverField.get(handler);
                                    if (brokerInstance != null) {
                                        // we finally have what we need
                                        ArtemisDetector.this.artemisInstance = brokerInstance;
                                        System.out.println("Jolokia: Detected Artemis broker instance "
                                            + brokerInstance.getClass().getName());
                                        brokerAvailable.set(true);
                                    }
                                } catch (NoSuchFieldException | SecurityException e) {
                                    brokerDetectionPossible.set(false);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // could be ClassNotFoundException because Artemis sets the builder class
                        // which is available in a JAR from lib/ directory and we didn't get there yet
                    }
                }
                return brokerClassLoaded.get() && (brokerAvailable.get() || !brokerDetectionPossible.get());
            },
            "Detected Artemis environment, but broker instance is not discovered after %d seconds");

        jvmAgentInitialization = true;

        return classloader.get();
    }

    @Override
    public ContainerLocator getContainerLocator(LogHandler logHandler) {
        if (jvmAgentInitialization) {
            // this detector was invoked during JVM Agent initialization
            return artemisInstance == null ? null : new ArtemisLocator();
        }

        // container locator may be called also when starting a WAR / Spring Boot agent where
        // we don't have access to java.lang.instrument.Instrumentation

        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            if (!Proxy.isProxyClass(server.getClass())) {
                // we can't grab an instance of the broker. It doesn't prevent DataUpdaters and CacheKeyProviders
                // specific to Artemis to be used, but they simply won't use broker configuration
                return null;
            } else {
                InvocationHandler handler = Proxy.getInvocationHandler(server);
                if (!jmxHandlerClass.equals(handler.getClass().getName())) {
                    // MBeanServer is a proxy, but using unsupported InvocationHandler
                    return null;
                } else {
                    Field serverField;
                    try {
                        serverField = handler.getClass().getDeclaredField("activeMQServer");
                        serverField.setAccessible(true);
                        Object brokerInstance = serverField.get(handler);
                        if (brokerInstance != null) {
                            // we finally have what we need
                            ArtemisDetector.this.artemisInstance = brokerInstance;
                            logHandler.info("Jolokia: Detected Artemis broker instance "
                                + brokerInstance.getClass().getName());
                            return new ArtemisLocator();
                        }
                    } catch (NoSuchFieldException | SecurityException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private class ArtemisLocator extends AbstractJolokiaService<ContainerLocator> implements ContainerLocator {
        protected ArtemisLocator() {
            super(ContainerLocator.class, 0);
        }

        @Override
        public <T> T container(Class<T> clazz) {
            Object instance = ArtemisDetector.this.artemisInstance;
            if (clazz != null && instance != null && clazz.isAssignableFrom(instance.getClass())) {
                return clazz.cast(instance);
            }
            return null;
        }
    }

}
