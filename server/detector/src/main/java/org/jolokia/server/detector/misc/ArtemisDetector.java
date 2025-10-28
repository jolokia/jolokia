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
import java.time.Duration;
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

    private static final String oldJmxBuilderClass = "org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerBuilder";
    private static final String newJmxBuilderClass = "org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder";
    private static final String oldJmxHandlerClass = "org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerBuilder$MBeanInvocationHandler";
    private static final String newJmxHandlerClass = "org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler";

    private static final String artemisBrokerClass = "org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl";

    private static final Duration ARTEMIS_DETECT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration ARTEMIS_DETECT_INTERVAL = Duration.ofMillis(200);
    private static final Duration ARTEMIS_DETECT_FINAL_DELAY = Duration.ofMillis(200);

//    // see org.apache.activemq.artemis.spi.core.security.jaas.HttpServerAuthenticator
//    // https://github.com/apache/activemq-artemis/blob/2.38.0/artemis-server/src/main/java/org/apache/activemq/artemis/spi/core/security/jaas/HttpServerAuthenticator.java#L52
//    private static final String REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME = "httpServerAuthenticator.requestSubjectAttribute";
//    private static final String DEFAULT_SUBJECT_ATTRIBUTE = "org.apache.activemq.artemis.jaasSubject";
//    private final String subjectRequestAttribute = System.getProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME, DEFAULT_SUBJECT_ATTRIBUTE);

    /** Artemis version detected from Maven metadata */
    private String artemisVersion = null;

    /**
     * Detected instance of {@code org.apache.activemq.artemis.core.server.management.GuardInvocationHandler}
     */
    private Object artemisGuardInvocationHandler = null;

    /**
     * Detected instance of {@code org.apache.activemq.artemis.core.server.ActiveMQServer} to make finding
     * default JMX domain easier
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
        return (int) ARTEMIS_DETECT_TIMEOUT.toMillis();
    }

    @Override
    protected int getDetectionInterval() {
        return (int) ARTEMIS_DETECT_INTERVAL.toMillis();
    }

    @Override
    protected int getDetectionFinalDelay() {
        return (int) ARTEMIS_DETECT_FINAL_DELAY.toMillis();
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

        // -Djavax.management.builder.initial (com.sun.jmx.defaults.JmxProperties.JMX_INITIAL_BUILDER)
        // There are two ways to configure RBAC in Artemis and both involve setting custom MBeanServerBuilder:
        // 1) through etc/management.xml (the property is set explicitly in code: org.apache.activemq.artemis.core.server.management.ManagementContext#init())
        // 2) through etc/broker.xml where user has to add -Djavax.management.builder.initial in etc/artemis.profile
        //    AND remove `<authorisation>` element from etc/management.xml
        //
        // in #1 org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerBuilder is used
        // in #2 org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder is used
        // https://activemq.apache.org/components/artemis/documentation/latest/management.html#jmx-authorization-in-broker-xml
        boolean oldArtemisBuilderUsed = oldJmxBuilderClass.equals(System.getProperty("javax.management.builder.initial"));
        boolean newArtemisBuilderUsed = newJmxBuilderClass.equals(System.getProperty("javax.management.builder.initial"));

        AtomicBoolean brokerClassLoaded = new AtomicBoolean(false);
        // if Artemis sets own mbean server builder, we can use it to access org.apache.activemq.artemis.core.server.management.GuardInvocationHandler
        AtomicBoolean guardAvailable = new AtomicBoolean(false);
        // however if (which shouldn't be possible) MBeanServer is not a proxy or the proxy is unknown,
        // we will skip the detection
        AtomicBoolean guardDetectionPossible = new AtomicBoolean(true);

        AtomicReference<ClassLoader> classloader = new AtomicReference<>();

        // We need to wait for two things
        //  - a classloader with jars from $ARTEMIS_HOME/lib + $ARTEMIS_INSTANCE/lib - for that we wait
        //    for org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl class to get its
        //    class loader, because this class is available in lib/artemis-server-<version>.jar
        //  - if Artemis sets javax.management.builder.initial, we need to grab the invocation handler,
        //    which may be:
        //     - for etc/management.xml RBAC:
        //       org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerBuilder.MBeanInvocationHandler
        //     - for etc/broker.xml RBAC:
        //       org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler
        //
        // From the invocation handler we can get org.apache.activemq.artemis.core.server.management.GuardInvocationHandler
        //  - etc/management.xml:
        //    org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerGuard which uses
        //    org.apache.activemq.artemis.core.server.management.JMXAccessControlList configured using
        //    org.apache.activemq.artemis.dto.ManagementContextDTO
        //    it uses org.apache.activemq.artemis.core.server.management.JMXAccessControlList directly
        //  - etc/broker.xml (no need to get it - it's the same instance):
        //    org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler
        //    it uses org.apache.activemq.artemis.core.server.ActiveMQServer#getSecurityStore().check()
        //
        // there's no need to deal with org.apache.activemq.artemis.core.server.management.impl.HawtioSecurityControlImpl
        // because it uses the above guards anyway

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
                if (loaded && guardDetectionPossible.get() && !guardAvailable.get()) {
                    try {
                        // we may be quicker here than:
                        //  - org.apache.activemq.artemis.cli.Artemis#verifyManagementDTO
                        //  - org.apache.activemq.artemis.core.server.management.ManagementContext#init
                        // so we should not call ManagementFactory.getPlatformMBeanServer() too early
                        String builderClass = System.getProperty("javax.management.builder.initial");
                        if (!(oldJmxBuilderClass.equals(builderClass) || newJmxBuilderClass.equals(builderClass))) {
                            // we'll check next tick
                            return false;
                        }
                        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                        if (!Proxy.isProxyClass(server.getClass())) {
                            // Artemis didn't set known proxy as the handler
                            guardDetectionPossible.set(false);
                        } else {
                            InvocationHandler handler = Proxy.getInvocationHandler(server);
                            String ihClass = handler.getClass().getName();
                            if (!(oldJmxHandlerClass.equals(ihClass) || newJmxHandlerClass.equals(ihClass))) {
                                guardDetectionPossible.set(false);
                            } else {
                                // at this stage we _have to_ find the guard or fail
                                // etc/management.xml case
                                if (oldJmxHandlerClass.equals(ihClass)) {
                                    Field guardField;
                                    try {
                                        // it's a static field
                                        Class<?> c = handler.getClass().getClassLoader().loadClass(oldJmxBuilderClass);
                                        guardField = c.getDeclaredField("guard");
                                        guardField.setAccessible(true);
                                        Object guard = guardField.get(null);
                                        if (guard != null) {
                                            ArtemisDetector.this.artemisGuardInvocationHandler = guard;
                                            guardAvailable.set(true);
                                        }
                                    } catch (NoSuchFieldException | SecurityException | ClassNotFoundException e) {
                                        guardDetectionPossible.set(false);
                                    }
                                }
                                // etc/broker.xml case
                                if (newJmxHandlerClass.equals(ihClass)) {
                                    // let's ensure the broker is already set in the handler
                                    Field serverField;
                                    try {
                                        serverField = handler.getClass().getDeclaredField("activeMQServer");
                                        serverField.setAccessible(true);
                                        Object brokerInstance = serverField.get(handler);
                                        if (brokerInstance != null) {
                                            ArtemisDetector.this.artemisInstance = brokerInstance;
                                            ArtemisDetector.this.artemisGuardInvocationHandler = handler;
                                            guardAvailable.set(true);
                                        }
                                    } catch (NoSuchFieldException | SecurityException e) {
                                        guardDetectionPossible.set(false);
                                    }
                                }
                                if (guardAvailable.get()) {
                                    System.out.println("Jolokia: Detected Artemis guard handler " + ihClass);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // could be ClassNotFoundException because Artemis sets the builder class
                        // which is available in a JAR from lib/ directory and we didn't get there yet
                    }
                }
                return brokerClassLoaded.get() && (guardAvailable.get() || !guardDetectionPossible.get());
            },
            "Detected Artemis environment, but Broker and MBeanServer guard instances are not discovered after %d seconds");

        jvmAgentInitialization = true;

        return classloader.get();
    }

    @Override
    public ContainerLocator getContainerLocator(LogHandler logHandler) {
        if (jvmAgentInitialization) {
            // this detector was invoked during JVM Agent initialization
            return artemisGuardInvocationHandler == null ? null : new ArtemisLocator();
        }

        // container locator may be called also when starting a WAR / Spring Boot agent where
        // we don't have access to java.lang.instrument.Instrumentation

        ArtemisLocator result = null;
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            if (!Proxy.isProxyClass(server.getClass())) {
                // Artemis didn't set known proxy as the handler
                return null;
            } else {
                InvocationHandler handler = Proxy.getInvocationHandler(server);
                String ihClass = handler.getClass().getName();
                if (!(oldJmxHandlerClass.equals(ihClass) || newJmxHandlerClass.equals(ihClass))) {
                    return null;
                } else {
                    // etc/management.xml case
                    if (oldJmxHandlerClass.equals(ihClass)) {
                        Field guardField;
                        try {
                            Class<?> c = handler.getClass().getClassLoader().loadClass(oldJmxBuilderClass);
                            guardField = c.getDeclaredField("guard");
                            guardField.setAccessible(true);
                            Object guard = guardField.get(null);
                            if (guard != null) {
                                ArtemisDetector.this.artemisGuardInvocationHandler = guard;
                                result = new ArtemisLocator();
                            }
                        } catch (NoSuchFieldException | SecurityException ignored) {
                        }
                    }
                    // etc/broker.xml case
                    if (newJmxHandlerClass.equals(ihClass)) {
                        // let's ensure the broker is already set in the handler
                        Field serverField;
                        try {
                            serverField = handler.getClass().getDeclaredField("activeMQServer");
                            serverField.setAccessible(true);
                            Object brokerInstance = serverField.get(handler);
                            if (brokerInstance != null) {
                                ArtemisDetector.this.artemisInstance = brokerInstance;
                                ArtemisDetector.this.artemisGuardInvocationHandler = handler;
                                result = new ArtemisLocator();
                            }
                        } catch (NoSuchFieldException | SecurityException ignored) {
                        }
                    }
                    if (result != null) {
                        logHandler.info("Jolokia: Detected Artemis guard handler " + ihClass);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private class ArtemisLocator extends AbstractJolokiaService<ContainerLocator> implements ContainerLocator {
        protected ArtemisLocator() {
            super(ContainerLocator.class, 0);
        }

        @Override
        public <T> T locate(Class<T> clazz) {
            if (clazz != null) {
                if (ArtemisDetector.this.artemisGuardInvocationHandler != null
                        && clazz.isAssignableFrom(ArtemisDetector.this.artemisGuardInvocationHandler.getClass())) {
                    return clazz.cast(ArtemisDetector.this.artemisGuardInvocationHandler);
                }
                if (ArtemisDetector.this.artemisInstance != null
                        && clazz.isAssignableFrom(ArtemisDetector.this.artemisInstance.getClass())) {
                    return clazz.cast(ArtemisDetector.this.artemisInstance);
                }
            }
            return null;
        }
    }

}
