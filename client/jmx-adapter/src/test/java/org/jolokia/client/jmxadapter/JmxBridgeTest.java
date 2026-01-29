/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.rmi.registry.LocateRegistry;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.jmxadapter.beans.MBeanExample;
import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.server.core.Version;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>Tests that operate on 3 different {@link MBeanServerConnection servers}:<ul>
 *     <li>{@link ManagementFactory#getPlatformMBeanServer() direct platform MBeanServer}</li>
 *     <li>{@link javax.management.remote.rmi.RMIConnector RMI implementation} of {@link JMXConnector}</li>
 *     <li>{@link RemoteJmxAdapter Jolokia implementation} of {@link JMXConnector}</li>
 * </ul></p>
 *
 * <p>{@link javax.management.remote.rmi.RMIConnector} used to be configured with properties like
 * {@code -Dcom.sun.management.jmxremote.port} but now we configure it programmatically.</p>
 */
public class JmxBridgeTest {

    private static final Logger LOG = Logger.getLogger(JmxBridgeTest.class.getName());

    private static final ObjectName RUNTIME = RemoteJmxAdapter.getObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
    private static final ObjectName THREADING = RemoteJmxAdapter.getObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
    private static final ObjectName MEMORY = RemoteJmxAdapter.getObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
    private static final ObjectName DIAGNOSTICS = RemoteJmxAdapter.getObjectName("com.sun.management:type=DiagnosticCommand");
    private static final ObjectName HOTSPOT_DIAGNOSTICS = RemoteJmxAdapter.getObjectName("com.sun.management:type=HotSpotDiagnostic");

    private JolokiaServer server;

    private MBeanServerConnection platform;

    private JMXServiceURL jolokiaConnectorURL;
    private JMXConnector jolokiaConnector;
    private RemoteJmxAdapter jolokia;

    private JMXConnectorServer rmiConnectorServer;
    private JMXConnector rmiConnector;
    private MBeanServerConnection rmi;

    private static final QueryExp QUERY = Query.or(
        Query.anySubString(Query.classattr(), Query.value("Object")),
        Query.anySubString(Query.classattr(), Query.value("String"))
    );

    // Safe values for testing setting attributes
    private static final Map<String, Object> ATTRIBUTE_REPLACEMENTS = Map.of(
            "jolokia:type=Config.Debug", true,
            "jolokia:type=Config.HistoryMaxEntries", 20,
            "jolokia:type=Config.MaxDebugEntries", 50,
            "java.lang:type=ClassLoading.Verbose", true,
            "java.lang:type=Threading.ThreadContentionMonitoringEnabled", true
    );

    private int agentPort;
    private int rmiConnectorPort;
    private int rmiRegistryPort;

    private ObjectName jfr;
    private boolean jfrAvailable;
    private static ObjectName mBeanExampleName;

    @BeforeClass
    public void startAgent() throws Exception {
        int agentPort = EnvTestUtil.getFreePort();
        JolokiaServerConfig config = new JolokiaServerConfig(Map.of(
            "port", Integer.toString(agentPort),
            "agentId", "local-jvm",
            "debug", "false"
        ));
        server = new JolokiaServer(config);
        server.start(false);
        LOG.info("Jolokia JVM Agent (test) started on port " + agentPort);

        platform = ManagementFactory.getPlatformMBeanServer();

        jfr = ObjectName.getInstance("jdk.management.jfr:type=FlightRecorder");
        jfrAvailable = platform.isRegistered(jfr);

        int rmiConnectorPort = EnvTestUtil.getFreePort();
        int rmiRegistryPort = EnvTestUtil.getFreePort();

        // RMI Registry - usually at port 1099
        LocateRegistry.createRegistry(rmiRegistryPort);

        mBeanExampleName = ObjectName.getInstance("jolokia.test:name=MBeanExample");
        platform.createMBean(MBeanExample.class.getName(), mBeanExampleName);

        // RMI Connector - JMX over RMI
        JMXServiceURL rmiConnectorURL = new JMXServiceURL("rmi", "localhost", rmiConnectorPort, "/jndi/rmi://127.0.0.1:" + rmiRegistryPort + "/jmxrmi");
        rmiConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(rmiConnectorURL, Collections.emptyMap(), (MBeanServer) platform);
        rmiConnectorServer.start();
        rmiConnector = JMXConnectorFactory.connect(rmiConnectorURL);
        rmi = rmiConnector.getMBeanServerConnection();

        // Jolokia Connector - JMX over Jolokia Client
        jolokiaConnectorURL = new JMXServiceURL("jolokia+http", "127.0.0.1", agentPort, "/jolokia");
        jolokiaConnector = JMXConnectorFactory.connect(jolokiaConnectorURL);
        jolokia = (RemoteJmxAdapter) jolokiaConnector.getMBeanServerConnection();

//        ToOpenTypeConverter.cacheType(
//            ToOpenTypeConverter.introspectComplexTypeFrom(FieldWithMoreElementsThanTheType.class),
//            "jolokia.test:name=MBeanExample.Field");
    }

    @AfterClass
    public void stopAgent() throws Exception {
        jolokiaConnector.close();
        rmiConnector.close();
        rmiConnectorServer.stop();
        server.stop();
        LOG.info("Jolokia JVM Agent (test) stopped");
    }

    // ---- Data providers for executing @Test methods with parameters

    @DataProvider
    public static Object[][] nameAndQueryCombinations() {
        return new Object[][] {
                { null, null },
                { RUNTIME, null },
                { null, QUERY },
                { RUNTIME, QUERY }
        };
    }

    /**
     * A list of all known {@code java.lang:type=Xxx} MBeans - platform MBeans.
     * See {@code org.jolokia.service.jmx.handler.ListHandler#cachePlatformMbeans()}.
     */
    @DataProvider
    public static Object[][] platformMBeans() {
        List<Object[]> testData = new LinkedList<>();
        for (Class<? extends PlatformManagedObject> cls : ManagementFactory.getPlatformManagementInterfaces()) {
            for (PlatformManagedObject managedObject : ManagementFactory.getPlatformMXBeans(cls)) {
                testData.add(new Object[] { managedObject.getObjectName(), cls });
            }
        }
        return testData.toArray(new Object[testData.size()][2]);
    }

    /**
     * A list of all available MBeans - hopefully it's not very dynamic during the test execution. Remember
     * that a lot of them provide highly dynamic data (memory, cpu, ...)
     */
    @DataProvider
    public static Object[][] allAvailableMBeans() {
        final Set<ObjectName> names = ManagementFactory.getPlatformMBeanServer().queryNames(null, null);
        final Object[][] result = new Object[names.size()][1];
        int index = 0;
        for (ObjectName name : names) {
            result[index++][0] = name;
        }
        return result;
    }

    @DataProvider
    public static Object[][] threadOperationsToCompare() {
        return new Object[][] {
                {
                        THREADING,
                        "getThreadInfo",
                        new Object[] { Thread.currentThread().getId() },
                        new String[] { "long" } },
                {
                        THREADING,
                        "getThreadInfo",
                        new Object[] { Thread.currentThread().getId(), 10 },
                        new String[] { "long", "int" } },
                {
                        THREADING,
                        "getThreadInfo",
                        new Object[] { new long[] { Thread.currentThread().getId() } },
                        new String[] { "[J" } },
                {
                        THREADING,
                        "getThreadInfo",
                        new Object[] { new long[] { Thread.currentThread().getId() }, 10 },
                        new String[] { "[J", "int" } },
                {
                        THREADING,
                        "getThreadInfo",
                        new Object[] { new long[] { Thread.currentThread().getId() }, true, true },
                        new String[] { "[J", "boolean", "boolean" } }
        };
    }

    @DataProvider
    public static Object[][] safeOperationsToCall() {
        return new Object[][] {
                { THREADING, "findDeadlockedThreads", null },
                { MEMORY, "gc", null },
                { DIAGNOSTICS, "vmCommandLine", null },
                { HOTSPOT_DIAGNOSTICS, "getVMOption", new Object[] { "MinHeapFreeRatio" } },
                { HOTSPOT_DIAGNOSTICS, "setVMOption", new Object[] { "HeapDumpOnOutOfMemoryError", "true" } },
                { mBeanExampleName, "doMapOperation", null },
                { mBeanExampleName, "doEmptySetOperation", null }
        };
    }

    // ---- Tests using @DataProviders

    @Test(dataProvider = "platformMBeans")
    public void testInstanceOf(final ObjectName objectName, final Class<?> klass) throws Exception {
        final String className = klass.getName();
        assertEquals(platform.isInstanceOf(objectName, className), jolokia.isInstanceOf(objectName, className),
                objectName.getCanonicalName() + " instanceof " + klass);

        // MXBean proxy using Jolokia JMXConnector
        final Object proxy = klass.cast(ManagementFactory.newPlatformMXBeanProxy(jolokia, objectName.getCanonicalName(), klass));

        // test at least one method through the proxy to ensure it works
        for (final Method method : klass.getDeclaredMethods()) {
            if ((method.getName().startsWith("get") || method.getName().startsWith("is")) && method.getParameterTypes().length == 0) {
                try {
                    method.invoke(proxy);
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof UnsupportedOperationException)) {
                        // some MBeans have unsupported methods, ignore
                        // try to call the same on on platform mbean server and see if we get the same error there
                        // this issue occurs for some JDK internal MBeans. For example
                        // com.sun.management.internal.GcInfoCompositeData.getBaseGcInfoCompositeType constructs
                        // the composite type arbitrarily and Jolokia uses generic algorithm
                        // also there's a problem with EventTypes attribute for JFR: https://bugs.openjdk.org/browse/JDK-8308877
                        final Object nativeProxy = ManagementFactory
                                .newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(),
                                        objectName.getCanonicalName(), klass);
                        try {
                            method.invoke(nativeProxy);
                            System.err.println("Failed calling " + method + " on " + objectName + " but succeeds with native proxy ");
                            throw e;
                        } catch (InvocationTargetException ignore) {
                        }
                    }
                }
            }
        }
    }

    @Test
    @Ignore("Tests comparing platform and Jolokia access are in JmxConnectorTest.java now")
    public void testTypesOfUnsafeAttributes(final ObjectName objectName, final String attributeName) {
    }

    @Test(dataProvider = "nameAndQueryCombinations")
    public void testNames(ObjectName name, QueryExp query) throws IOException {
        assertEquals(platform.queryNames(name, query), jolokia.queryNames(name, query));
    }

    @Test(dataProvider = "nameAndQueryCombinations")
    public void testInstances(ObjectName name, QueryExp query) throws IOException {
        assertEquals(platform.queryMBeans(name, query), jolokia.queryMBeans(name, query));
    }

    @Test(dataProvider = "threadOperationsToCompare")
    public void testCompareThreadMethods(ObjectName name, String operation, Object[] arguments, String[] signature)
            throws Exception {
        assertEquals(
            jolokia.invoke(name, operation, arguments, signature).getClass(),
            platform.invoke(name, operation, arguments, signature).getClass()
        );
    }

    @Test(dataProvider = "safeOperationsToCall")
    public void testInvoke(ObjectName name, String operation, Object[] arguments) throws Exception {
        try {
            for (MBeanOperationInfo operationInfo : jolokia.getMBeanInfo(name).getOperations()) {
                if (operationInfo.getName().equals(operation)
                        && argumentCountIsCompatible(arguments, operationInfo.getSignature().length)) {
                    String[] signature = createSignature(operationInfo);
                    assertEquals(
                            jolokia.invoke(name, operation, arguments, signature),
                            platform.invoke(name, operation, arguments, signature));
                }
            }
        } catch (InstanceNotFoundException e) {
            throw new SkipException(e.getMessage());
        }
    }

    private boolean argumentCountIsCompatible(Object[] arguments, int argumentCount) {
        return (arguments == null && argumentCount == 0) || arguments != null && argumentCount == arguments.length;
    }

    private String[] createSignature(MBeanOperationInfo operationInfo) {
        if (operationInfo.getSignature().length == 0) {
            return null;//simulate JVisualVM by returning null instead of an empty array
        }
        String[] signature = new String[operationInfo.getSignature().length];
        for (int i = 0; i < signature.length; i++) {
            signature[i] = operationInfo.getSignature()[i].getType();
        }
        return signature;
    }

    @Test(dataProvider = "allAvailableMBeans")
    public void testInstances(ObjectName name) throws Exception {
        final ObjectInstance nativeInstance = platform.getObjectInstance(name);
        final ObjectInstance jolokiaInstance = jolokia.getObjectInstance(name);
        assertEquals(jolokiaInstance, nativeInstance);

        assertEquals(
                platform.isInstanceOf(jolokiaInstance.getObjectName(), jolokiaInstance.getClassName()),
                jolokia.isInstanceOf(jolokiaInstance.getObjectName(), jolokiaInstance.getClassName()));

        assertEquals(platform.isRegistered(name), jolokia.isRegistered(name));

        final Class<?> klass = Class.forName(jolokiaInstance.getClassName());
        // check that inheritance works the same for both interfaces
        if (klass.getSuperclass() != null) {
            assertEquals(
                    platform.isInstanceOf(jolokiaInstance.getObjectName(), klass.getSuperclass().toString()),
                    jolokia.isInstanceOf(jolokiaInstance.getObjectName(), klass.getSuperclass().toString()));
            if (klass.getInterfaces().length > 0) {
                assertEquals(
                        platform.isInstanceOf(jolokiaInstance.getObjectName(), klass.getInterfaces()[0].toString()),
                        jolokia.isInstanceOf(jolokiaInstance.getObjectName(), klass.getInterfaces()[0].toString()));
            }
        }
    }

    @Test(dataProvider = "allAvailableMBeans")
    public void testMBeanInfo(ObjectName name) throws Exception {
        final MBeanInfo jolokiaMBeanInfo = jolokia.getMBeanInfo(name);
        final MBeanInfo nativeMBeanInfo = platform.getMBeanInfo(name);

        assertEquals(jolokiaMBeanInfo.getDescription(), nativeMBeanInfo.getDescription());
        assertEquals(jolokiaMBeanInfo.getClassName(), nativeMBeanInfo.getClassName());
        assertEquals(jolokiaMBeanInfo.getAttributes().length, nativeMBeanInfo.getAttributes().length);
        assertEquals(jolokiaMBeanInfo.getOperations().length, nativeMBeanInfo.getOperations().length);

        final AttributeList replacementValues = new AttributeList();
        final AttributeList originalValues = new AttributeList();
        final List<String> attributeNames = new LinkedList<>();

        for (MBeanAttributeInfo attribute : jolokiaMBeanInfo.getAttributes()) {
            final String qualifiedName = name + "." + attribute.getName();
            try {
                final Object jolokiaAttributeValue = jolokia.getAttribute(name, attribute.getName());
                final Object nativeAttributeValue = platform.getAttribute(name, attribute.getName());
                // data type probably not so important (ie. long vs integer), as long as value is close enough
                if (jolokiaAttributeValue instanceof Number jn && nativeAttributeValue instanceof Number nn) {
                    // let's simply check if they are of the same sign
                    assertTrue(jn.doubleValue() * nn.doubleValue() >= 0);
                } else if (jolokiaAttributeValue instanceof CompositeData jcd && nativeAttributeValue instanceof CompositeData ncd) {
                    // com.sun.management.internal.GcInfoCompositeData is messing the comparison
                    assertTrue(ncd.getCompositeType().keySet().containsAll(jcd.getCompositeType().keySet()));
                } else {
                    assertEquals(jolokiaAttributeValue, nativeAttributeValue, "Attribute mismatch: " + qualifiedName);
                }
                if (attribute.isWritable()) {
                    final Object newValue = ATTRIBUTE_REPLACEMENTS.get(qualifiedName);

                    if (newValue != null) {
                        final Attribute newAttribute = new Attribute(attribute.getName(), newValue);
                        replacementValues.add(newAttribute);
                        jolokia.setAttribute(name, newAttribute);
                        // use native connection and verify that attribute is now new value
                        assertEquals(platform.getAttribute(name, attribute.getName()), newValue);
                        // restore original value
                        final Attribute restoreAttribute =
                                new Attribute(attribute.getName(), nativeAttributeValue);
                        jolokia.setAttribute(name, restoreAttribute);
                        originalValues.add(restoreAttribute);
                        attributeNames.add(attribute.getName());
                        // now do multi argument setting
                        jolokia.setAttributes(name, replacementValues);
                        assertEquals(platform.getAttribute(name, attribute.getName()), newValue);
                        // and restore
                        jolokia.setAttributes(name, originalValues);
                        jolokia.getAttributes(name, attributeNames.toArray(new String[0]));
                    }
                }
            } catch (RuntimeMBeanException e) {
                if (!(e.getCause() instanceof UnsupportedOperationException)) {
                    throw new RuntimeException(e.getCause());
                }
            }
        }
    }

    // ---- Tests not using @DataProviders

    @Test
    public void testRecordingSettings() throws Exception {
        if (!jfrAvailable) {
            throw new SkipException("FlightRecorder bean is not available");
        }

        Object newRecording = jolokia.invoke(jfr, "newRecording", new Object[0], new String[0]);
        Object recordingOptions = jolokia.invoke(jfr, "getRecordingOptions", new Object[] { newRecording }, new String[] { "long" });

        if (recordingOptions instanceof TabularData td) {
            if (td.containsKey(new String[] { "destination" })) {
                OpenType<?> descriptionType = td.get(new String[] { "destination" }).getCompositeType().getType("value");
                assertEquals(descriptionType, SimpleType.STRING);
            }
        } else {
            fail("Expected TabularData from getRecordingOptions");
        }
    }

    @Test
    public void testThreadingDetails() throws Exception {
        long[] ids = (long[]) platform.getAttribute(THREADING, "AllThreadIds");
        for (long id : ids) {
            jolokia.invoke(THREADING, "getThreadInfo", new Object[] { id }, new String[] { "long" });
        }
        jolokia.invoke(THREADING, "findDeadlockedThreads", new Object[0], new String[0]);
    }

    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void testNonExistentMBeanInstance() throws Exception {
        jolokia.getObjectInstance(RemoteJmxAdapter.getObjectName("notexistant.domain:type=NonSense"));
    }

    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void testNonExistentMBeanInfo() throws Exception {
        jolokia.getMBeanInfo(RemoteJmxAdapter.getObjectName("notexistant.domain:type=NonSense"));
    }

    @Test
    public void testFeatureNotSupportedOnServerSide() throws Exception {
        try {
            platform.invoke(mBeanExampleName, "unsupportedOperation", new Object[0], new String[0]);
            fail("Expected RuntimeMBeanException");
        } catch (RuntimeMBeanException expected) {
        }
        try {
            jolokia.invoke(mBeanExampleName, "unsupportedOperation", new Object[0], new String[0]);
            fail("Expected RuntimeMBeanException");
        } catch (RuntimeMBeanException expected) {
        }
        try {
            rmi.invoke(mBeanExampleName, "unsupportedOperation", new Object[0], new String[0]);
            fail("Expected RuntimeMBeanException");
        } catch (RuntimeMBeanException expected) {
        }
    }

    @Test
    public void testUnexpectedlyFailingOperation() throws Exception {
        try {
            platform.invoke(mBeanExampleName, "unexpectedFailureMethod", new Object[0], new String[0]);
            fail("Expected RuntimeMBeanException");
        } catch (RuntimeMBeanException expected) {
            assertTrue(expected.getCause() instanceof NullPointerException);
        }
        try {
            jolokia.invoke(mBeanExampleName, "unexpectedFailureMethod", new Object[0], new String[0]);
            fail("Expected RuntimeMBeanException");
        } catch (RuntimeMBeanException expected) {
            assertTrue(expected.getCause() instanceof NullPointerException);
        }
        try {
            rmi.invoke(mBeanExampleName, "unexpectedFailureMethod", new Object[0], new String[0]);
            fail("Expected RuntimeMBeanException");
        } catch (RuntimeMBeanException expected) {
            assertTrue(expected.getCause() instanceof NullPointerException);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void ensureThatIOExceptionIsChanneledOut() throws IOException {
        new RemoteJmxAdapter(new JolokiaClientBuilder().url("http://localhost:10/jolokia").build()).queryMBeans(null, null);
    }

    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void testGetNonExistentAttribute() throws Exception {
        jolokia.getAttribute(RUNTIME, "DoesNotExist");
    }

    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void testSetNonExistentAttribute() throws Exception {
        jolokia.setAttribute(RUNTIME, new Attribute("DoesNotExist", false));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void testSetInvalidAttributeValue() throws Exception {
        jolokia.setAttribute(RemoteJmxAdapter.getObjectName("jolokia:type=History,agent=local-jvm"), new Attribute("HistoryMaxEntries", null));
    }

    @Test
    public void testThatWeAreAbleToInvokeOperationWithOverloadedSignature() throws Exception {
        // Invoke method that has both primitive and boxed Long as possible input
        jolokia.invoke(THREADING, "getThreadInfo", new Object[] { 1L }, new String[] { "long" });
        jolokia.invoke(THREADING, "getThreadInfo", new Object[] { new long[] { 1L } }, new String[] { "[J" });
    }

    @Test
    public void verifyUnsupportedFunctions() {
        // ensure that methods give the expected exception and nothing else
        try {
            jolokia.createMBean("java.lang.Object", RUNTIME);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.createMBean("java.lang.Object", RUNTIME, RUNTIME);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.createMBean("java.lang.Object", RUNTIME, new Object[0], new String[0]);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.createMBean("java.lang.Object", RUNTIME, RUNTIME, new Object[0], new String[0]);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.unregisterMBean(RUNTIME);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.addNotificationListener(RUNTIME, (NotificationListener) null, null, null);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.addNotificationListener(RUNTIME, RUNTIME, null, null);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.removeNotificationListener(RUNTIME, RUNTIME);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.removeNotificationListener(RUNTIME, RUNTIME, null, null);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.removeNotificationListener(RUNTIME, (NotificationListener) null);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            jolokia.removeNotificationListener(RUNTIME, (NotificationListener) null, null, null);
            fail("Operation should not be supported by adapter");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void testOverallOperations() throws IOException {
        assertEquals(jolokia.getMBeanCount(), platform.getMBeanCount(), "Number of MBeans are the same");
        assertEquals(jolokia.getDefaultDomain(), platform.getDefaultDomain(), "Default domain");

        assertEqualsNoOrder(jolokia.getDomains(), platform.getDomains(), "Domain list is the same");

        assertEquals(jolokia.agentVersion, Version.getAgentVersion());
        assertEquals(jolokia.protocolVersion, Version.getProtocolVersion());

        assertTrue(jolokia.getId().endsWith("-jvm"));
    }

    @Test
    public void testConnector() throws IOException {
        JMXConnector connector = JMXConnectorFactory.newJMXConnector(jolokiaConnectorURL, Collections.emptyMap());

        final List<JMXConnectionNotification> receivedNotifications = new LinkedList<>();

        final Object handback = "foobar";

        connector.addConnectionNotificationListener((notification, hb) -> {
            assertTrue(notification instanceof JMXConnectionNotification);
            receivedNotifications.add((JMXConnectionNotification) notification);
        }, null, handback);
        connector.connect();
        assertEquals(((JolokiaJmxConnector) connector).getJolokiaProtocol(), "http");
        assertEquals(receivedNotifications.get(0).getSource(), connector);
        assertEquals(receivedNotifications.get(0).getType(), JMXConnectionNotification.OPENED);
        assertEquals(connector.getMBeanServerConnection(), jolokia);

        receivedNotifications.clear();

        connector.close();
        assertEquals(receivedNotifications.get(0).getSource(), connector);
        assertEquals(receivedNotifications.get(0).getType(), JMXConnectionNotification.CLOSED);
        connector.connect(Collections.emptyMap());
        assertEquals(connector.getMBeanServerConnection(), jolokia);
    }

    @Test(expectedExceptions = ConnectException.class)
    public void testConnectorIPv6() throws IOException {
        JMXServiceURL serviceURL = new JMXServiceURL("jolokia", "[::1]", agentPort, "/jolokia/");
        JMXConnector connector = JMXConnectorFactory.newJMXConnector(serviceURL, Collections.emptyMap());

        connector.addConnectionNotificationListener((notification, hb) -> assertTrue(notification instanceof JMXConnectionNotification), null, "foobar");
        connector.connect();
        connector.close();
    }

    @Test
    public void testGetAttributes() throws Exception {
        final String[] bothValidAndInvalid = new String[] { "Name", "Starttime", "StartTime" };
        assertEquals(jolokia.getAttributes(RUNTIME, bothValidAndInvalid), platform.getAttributes(RUNTIME, bothValidAndInvalid));

        final String[] onlyInvalid = new String[] { "Starttime" };
        assertEquals(jolokia.getAttributes(RUNTIME, onlyInvalid), platform.getAttributes(RUNTIME, onlyInvalid));

        final String[] singleValid = new String[] { "StartTime" };
        assertEquals(jolokia.getAttributes(RUNTIME, singleValid), platform.getAttributes(RUNTIME, singleValid));

        final String[] multipleValid = new String[] { "Name", "StartTime" };
        assertEquals(jolokia.getAttributes(RUNTIME, multipleValid), platform.getAttributes(RUNTIME, multipleValid));
    }

}
