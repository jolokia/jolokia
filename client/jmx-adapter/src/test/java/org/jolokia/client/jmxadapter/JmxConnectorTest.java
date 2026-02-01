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
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.RuntimeMBeanException;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfoSupport;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jolokia.client.jmxadapter.beans.AllOpenTypes;
import org.jolokia.client.jmxadapter.beans.Custom;
import org.jolokia.client.jmxadapter.beans.Custom2;
import org.jolokia.client.jmxadapter.beans.CustomDynamic;
import org.jolokia.client.jmxadapter.beans.CustomDynamicMBean;
import org.jolokia.client.jmxadapter.beans.Example1;
import org.jolokia.client.jmxadapter.beans.Example1MXBean;
import org.jolokia.client.jmxadapter.beans.Example2;
import org.jolokia.client.jmxadapter.beans.Example2MBean;
import org.jolokia.client.jmxadapter.beans.ManyTypes;
import org.jolokia.client.jmxadapter.beans.Mixed;
import org.jolokia.client.jmxadapter.beans.Settable1;
import org.jolokia.client.jmxadapter.beans.Settable1MXBean;
import org.jolokia.client.jmxadapter.beans.SimpleIllegal1;
import org.jolokia.client.jmxadapter.beans.SimpleIllegal2;
import org.jolokia.client.jmxadapter.beans.SimpleIllegal3;
import org.jolokia.client.jmxadapter.beans.User;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests that compare results from {@link MBeanServer platform MBeanServer} and Jolokia implementation
 * of {@link javax.management.MBeanServerConnection}.
 */
public class JmxConnectorTest {

    private static final Logger LOG = Logger.getLogger(JmxConnectorTest.class.getName());

    private JolokiaServer server;
    private MBeanServer platform;

    private JMXConnector connector;

    private ObjectName allOpenTypesName;
    private ObjectName manyTypesName;
    private ObjectName customDynamicName;
    private ObjectName jfr;
    private ObjectName mixedName;

    @BeforeClass
    public void startJVMAgent() throws Exception {
        int port = EnvTestUtil.getFreePort();
        JolokiaServerConfig config = new JolokiaServerConfig(Map.of(
            "port", Integer.toString(port),
            "debug", "false"
        ));
        server = new JolokiaServer(config);
        server.start(false);
        LOG.info("Jolokia JVM Agent (test) started on port " + port);

        platform = ManagementFactory.getPlatformMBeanServer();

        Set<ObjectName> names = platform.queryNames(new ObjectName("jolokia:*"), null);
        names.forEach(name -> {
            try {
                if (!name.getCanonicalName().contains("History") && !name.getCanonicalName().contains("ServerHandler")
                        && !name.getCanonicalName().contains("type=Config")) {
                    platform.unregisterMBean(name);
                }
            } catch (Exception ignored) {
            }
        });

        jfr = ObjectName.getInstance("jdk.management.jfr:type=FlightRecorder");

        allOpenTypesName = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest1");
        platform.registerMBean(new AllOpenTypes(), allOpenTypesName);

        manyTypesName = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest2");
        platform.registerMBean(new ManyTypes(), manyTypesName);

        mixedName = ObjectName.getInstance("jolokia:test=Mixed");
        platform.registerMBean(new Mixed(), mixedName);

        // non-MXBean which should not be assumed to use key+value items for the rowType of TabularType
        customDynamicName = ObjectName.getInstance("jolokia:test=CustomDynamic");
        platform.registerMBean(new StandardMBean(new CustomDynamic(), CustomDynamicMBean.class) {
            @Override
            public MBeanInfo getMBeanInfo() {
                MBeanInfo info = super.getMBeanInfo();
                MBeanAttributeInfo original = info.getAttributes()[0];
                MBeanAttributeInfo[] arr = new MBeanAttributeInfo[1];
                try {
                    arr[0] = new OpenMBeanAttributeInfoSupport(original.getName(), original.getDescription(), CustomDynamic.type(), true, false, false);
                } catch (OpenDataException e) {
                    throw new RuntimeException(e);
                }
                return new MBeanInfo(info.getClassName(), info.getDescription(), arr, info.getConstructors(), info.getOperations(), info.getNotifications());
            }
        }, customDynamicName);

        JMXServiceURL serviceURL = new JMXServiceURL("jolokia+http", "127.0.0.1", port, "/jolokia");
        connector = JMXConnectorFactory.connect(serviceURL, env());

        TypeHelper.CACHE.clear();
    }

    protected Map<String, Object> env() {
        return Collections.emptyMap();
    }

    protected boolean useOpenTypeInformation() {
        return true;
    }

    @AfterClass
    public void stopJVMAgent() throws IOException {
        connector.close();
        server.stop();
        LOG.info("Jolokia JVM Agent (test) stopped");
    }

    @Test
    public void createConnectorUsingURI() throws Exception {
        assertTrue(connector instanceof JolokiaJmxConnector);
        assertTrue(connector.getMBeanServerConnection() instanceof RemoteJmxAdapter);
    }

    @Test
    public void getMBeanInfoForIntrospection() throws Exception {
        // for MXBean, real getMBeanInfo returns OpenMBeanAttributeInfoSupport for all attributes but:
        //  - primitive types (8)
        //  - arrays of primitive types (8)
        MBeanInfo platformInfo1 = platform.getMBeanInfo(allOpenTypesName);
        MBeanInfo platformInfo2 = platform.getMBeanInfo(manyTypesName);
        MBeanInfo jolokiaInfo1 = connector.getMBeanServerConnection().getMBeanInfo(allOpenTypesName);
        MBeanInfo jolokiaInfo2 = connector.getMBeanServerConnection().getMBeanInfo(manyTypesName);

        // according to JSR 174, primitive types NEVER use javax.management.openmbean.OpenMBeanAttributeInfo

        Object bytePrimitiveValue = connector.getMBeanServerConnection().getAttribute(allOpenTypesName, "PrimitiveByte");
        assertTrue(bytePrimitiveValue instanceof Byte);
        Object byteValue = connector.getMBeanServerConnection().getAttribute(allOpenTypesName, "Byte");
        assertTrue(byteValue instanceof Byte);
        bytePrimitiveValue = connector.getMBeanServerConnection().getAttribute(manyTypesName, "PrimitiveByte");
        assertTrue(bytePrimitiveValue instanceof Byte);
        byteValue = connector.getMBeanServerConnection().getAttribute(manyTypesName, "Byte");
        assertTrue(byteValue instanceof Byte);

        Object byteArrayPrimitiveValue = connector.getMBeanServerConnection().getAttribute(allOpenTypesName, "PrimitiveByteArray");
        assertTrue(byteArrayPrimitiveValue instanceof byte[]);
        Object byteArrayValue = connector.getMBeanServerConnection().getAttribute(allOpenTypesName, "ByteArray");
        assertTrue(byteArrayValue instanceof Byte[]);
        byteArrayPrimitiveValue = connector.getMBeanServerConnection().getAttribute(manyTypesName, "PrimitiveByteArray");
        assertTrue(byteArrayPrimitiveValue instanceof byte[]);
        byteArrayValue = connector.getMBeanServerConnection().getAttribute(manyTypesName, "ByteArray");
        assertTrue(byteArrayValue instanceof Byte[]);
    }

    @Test
    public void getMixedInfoForIntrospection() throws Exception {
        MBeanInfo platformInfo = platform.getMBeanInfo(mixedName);
        MBeanInfo jolokiaInfo = connector.getMBeanServerConnection().getMBeanInfo(mixedName);

        Map<String, MBeanAttributeInfo> platformAttrs = new TreeMap<>();
        Arrays.stream(platformInfo.getAttributes()).forEach(attr -> platformAttrs.put(attr.getName(), attr));
        Arrays.stream(jolokiaInfo.getAttributes()).forEach(attr -> {
            MBeanAttributeInfo p = platformAttrs.get(attr.getName());
            assertEquals(attr.getType(), p.getType());
            if (attr instanceof OpenMBeanAttributeInfoSupport openAttr) {
                assertEquals(openAttr.getOpenType(), ((OpenMBeanAttributeInfoSupport) p).getOpenType());
            }
        });

        Map<String, MBeanOperationInfo> platformOps = new TreeMap<>();
        Arrays.stream(platformInfo.getOperations()).forEach(op -> {
            String sig = TypeHelper.buildSignature(Arrays.stream(op.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new));
            platformOps.put(op.getName() + sig, op);
        });
        Arrays.stream(jolokiaInfo.getOperations()).forEach(op -> {
            String sig = TypeHelper.buildSignature(Arrays.stream(op.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new));
            MBeanOperationInfo p = platformOps.get(op.getName() + sig);
            assertEquals(op.getReturnType(), p.getReturnType());
            if (op instanceof OpenMBeanOperationInfoSupport openOp) {
                assertEquals(openOp.getReturnOpenType(), ((OpenMBeanOperationInfoSupport) p).getReturnOpenType());
            }
        });
    }

    @Test
    public void getJFRInfoForIntrospection() throws Exception {
        MBeanInfo platformInfo = platform.getMBeanInfo(jfr);
        MBeanInfo jolokiaInfo = connector.getMBeanServerConnection().getMBeanInfo(jfr);

        Map<String, MBeanAttributeInfo> platformAttrs = new TreeMap<>();
        Arrays.stream(platformInfo.getAttributes()).forEach(attr -> platformAttrs.put(attr.getName(), attr));
        Arrays.stream(jolokiaInfo.getAttributes()).forEach(attr -> {
            MBeanAttributeInfo p = platformAttrs.get(attr.getName());
            assertEquals(attr.getType(), p.getType());
            if (attr instanceof OpenMBeanAttributeInfoSupport openAttr) {
                assertEquals(openAttr.getOpenType(), ((OpenMBeanAttributeInfoSupport) p).getOpenType());
            }
        });

        Map<String, MBeanOperationInfo> platformOps = new TreeMap<>();
        Arrays.stream(platformInfo.getOperations()).forEach(op -> {
            String sig = TypeHelper.buildSignature(Arrays.stream(op.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new));
            platformOps.put(op.getName() + sig, op);
        });
        Arrays.stream(jolokiaInfo.getOperations()).forEach(op -> {
            String sig = TypeHelper.buildSignature(Arrays.stream(op.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new));
            MBeanOperationInfo p = platformOps.get(op.getName() + sig);
            assertEquals(op.getReturnType(), p.getReturnType());
            if (op instanceof OpenMBeanOperationInfoSupport openOp) {
                assertEquals(openOp.getReturnOpenType(), ((OpenMBeanOperationInfoSupport) p).getReturnOpenType());
            }
        });
    }

    @Test
    public void fourCombinationsOfMBeans() throws Exception {
        MBeanServer platform = ManagementFactory.getPlatformMBeanServer();

        ObjectName example1a = new ObjectName("jolokia:test=TypeHelperTest,name=example1a");
        ObjectName example1b = new ObjectName("jolokia:test=TypeHelperTest,name=example1b");
        ObjectName example2a = new ObjectName("jolokia:test=TypeHelperTest,name=example2a");
        ObjectName example2b = new ObjectName("jolokia:test=TypeHelperTest,name=example2b");

        // when JDK 17 (Temurin-17.0.18+8) starts, there are 26 MBeans registered.
        // Each can be nicely found in the heap dump using com.sun.jmx.mbeanserver.NamedObject objects stored
        // in com.sun.jmx.mbeanserver.Repository. NamedObject connects ObjectName with javax.management.DynamicMBean
        // and the above MBeans are stored using:
        //  - javax.management.StandardMBean (18)
        //     - com.sun.management:type=HotSpotDiagnostic
        //     - java.lang:type=MemoryPool,name=CodeHeap 'non-nmethods'
        //     - java.lang:type=MemoryPool,name=CodeHeap 'non-profiled nmethods'
        //     - java.lang:type=MemoryPool,name=CodeHeap 'profiled nmethods'
        //     - java.lang:type=MemoryPool,name=Compressed Class Space
        //     - java.lang:type=MemoryPool,name=G1 Eden Space,
        //     - java.lang:type=MemoryPool,name=G1 Old Gen
        //     - java.lang:type=MemoryPool,name=G1 Survivor Space
        //     - java.lang:type=MemoryPool,name=Metaspace
        //     - java.lang:type=ClassLoading
        //     - java.lang:type=Compilation
        //     - java.lang:type=OperatingSystem
        //     - java.lang:type=Runtime
        //     - java.lang:type=Threading
        //     - java.nio:type=BufferPool,name=direct
        //     - java.nio:type=BufferPool,name=mapped
        //     - java.nio:type=BufferPool,name=mapped - 'non-volatile memory'
        //     - java.util.logging:type=Logging
        //  - javax.management.StandardEmitterMBean extends javax.management.StandardMBean (5)
        //     - java.lang:type=GarbageCollector,name=G1 Old Generation
        //     - java.lang:type=GarbageCollector,name=G1 Young Generation
        //     - java.lang:type=MemoryManager,name=CodeCacheManager
        //     - java.lang:type=MemoryManager,name=Metaspace Manager
        //     - java.lang:type=Memory
        //  - directly, because the classes directly extend DynamicMBean (3)
        //     - com.sun.management.internal.DiagnosticCommandImpl implements javax.management.DynamicMBean
        //        - com.sun.management:type=DiagnosticCommand
        //     - jdk.management.jfr.FlightRecorderMXBeanImpl extends javax.management.StandardMBean
        //        - jdk.management.jfr:type=FlightRecorder
        //     - com.sun.jmx.mbeanserver.MBeanServerDelegateImpl implements javax.management.DynamicMBean
        //        - JMImplementation:type=MBeanServerDelegate
        //
        // StandardMBean (and FlightRecorderMXBeanImpl and StandardEmitterMBean by inheritance) have an important field "mbean" of
        // com.sun.jmx.mbeanserver.MBeanSupport class and there are two obvious implementations:
        //  - com.sun.jmx.mbeanserver.StandardMBeanSupport - 3 instances initially - actually only for Jolokia History and and Server
        //  - com.sun.jmx.mbeanserver.MXBeanSupport        - 24 instances initially
        //
        // com.sun.management:type=DiagnosticCommand always returns new MBeanInfo (with attr and op infos) without
        // caching. There are 44 operations in this MBean and these match (mostly) the commands from `jcmd <pid> help`
        //
        // There are only two instances of TabularType at the start of JVM
        // TabularType@4530 = "javax.management.openmbean.TabularType(
        //                         name=java.util.Map<java.lang.String, java.lang.String>,
        //                         rowType=javax.management.openmbean.CompositeType(
        //                             name=java.util.Map<java.lang.String, java.lang.String>,
        //                             items=(
        //                                 (itemName=key,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),
        //                                 (itemName=value,itemType=javax.management.openmbean.SimpleType(name=java.lang.String))
        //                             )
        //                         ),
        //                         indexNames=(key))"
        // TabularType@4531 = "javax.management.openmbean.TabularType(
        //                         name=java.util.Map<java.lang.String, java.lang.management.MemoryUsage>,
        //                         rowType=javax.management.openmbean.CompositeType(
        //                             name=java.util.Map<java.lang.String, java.lang.management.MemoryUsage>,
        //                             items=(
        //                                 (itemName=key,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),
        //                                 (itemName=value,itemType=javax.management.openmbean.CompositeType(
        //                                     name=java.lang.management.MemoryUsage,
        //                                     items=(
        //                                         (itemName=committed,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),
        //                                         (itemName=init,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),
        //                                         (itemName=max,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),
        //                                         (itemName=used,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long))
        //                                     )
        //                                 ))
        //                             )
        //                         ),
        //                         indexNames=(key))"
        //
        // but are used in 12 places thanks to caching in com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.mappings:
        // java.util.Map<java.lang.String, java.lang.String>
        //  - jdk.management.jfr:type=FlightRecorder:
        //     - getRecordingOptions() return type
        //     - getRecordingSettings() return type
        //     - setRecordingOptions() parameter 2
        //     - setRecordingSettings() parameter 2
        //     - openStream() parameter 2
        //     - Recordings attribute: settings item of CompositeType of elementType of ArrayType of the attribute
        //     - Configurations attribute: settings item of CompositeType of elementType of ArrayType of the attribute
        //  - java.lang:type=Runtime:
        //     - SystemProperties attribute - directly a type of this attribute
        // java.util.Map<java.lang.String, java.lang.management.MemoryUsage>
        //  - java.lang:type=GarbageCollector,name=G1 Old Generation and java.lang:type=GarbageCollector,name=G1 Young Generation:
        //     - LastGcInfo attribute: memoryUsageAfterGc item of CompositeType of the attribute
        //     - LastGcInfo attribute: memoryUsageBeforeGc item of CompositeType of the attribute
        //
        // There are 13 instances of CompositeType at the start of JVM
        //  - all have className = javax.management.openmbean.CompositeData
        //  - typeName and description fields of CompositeType use the same String instance
        //  - only 2 CompositeTypes use "key" (always SimpleType.STRING) and "value" items. For these typeNames:
        //     - java.util.Map<java.lang.String, java.lang.String>
        //     - java.util.Map<java.lang.String, java.lang.management.MemoryUsage>
        //  - other 11 types are:
        //     - com.sun.management.GcInfo - duration, endTime, id, memoryUsageAfterGc, memoryUsageBeforeGc, startTime
        //     - com.sun.management.VMOption - name, origin, value, writeable
        //     - java.lang.management.LockInfo - className, identityHashCode
        //     - java.lang.management.MemoryUsage - committed, init, max, usage
        //     - java.lang.management.MonitorInfo - className, identityHashCode, lockedStackDepth, lockedStackFrame
        //     - java.lang.management.ThreadInfo - blockedCount, blockedTime, daemon, inNative, lockInfo, lockName, lockOwnerId, lockOwnerName, lockedMonitors, lockedSynchronizers, priority, stackTrace, suspended, threadId, threadName, threadState, waitedCount, waitedTime
        //       here are some minor additions between JDK 1.8 and JDK 25
        //     - java.lang.StackTraceElement - classLoaderName, className, fileName, lineNumber, methodName, moduleName, moduleVersion, nativeMethod
        //       many changes between JDK 1.8 and JDK 25, but no changes between JDK 17 and JDK 25
        //     - jdk.management.jfr.ConfigurationInfo - contents, description, label, name, provider, settings
        //     - jdk.management.jfr.EventTypeInfo - categoryNames, description, id, label, name, settingDescriptors
        //     - jdk.management.jfr.RecordingInfo - destination, dumpOnExit, duration, id, maxAge, maxSize, name, settings, size, startTime, state, stopTime, toDisk
        //     - jdk.management.jfr.SettingDescriptorInfo - contentType, defaultValue, description, label, name, typeName
        //
        // 1 wrapped in com.sun.jmx.mbeanserver.MXBeanSupport
        platform.registerMBean(new Example1(), example1a);
        // 3 wrapped in com.sun.jmx.mbeanserver.StandardMBeanSupport
        platform.registerMBean(new StandardMBean(new Example1(), Example1MXBean.class), example1b);
        platform.registerMBean(new Example2(), example2a);
        platform.registerMBean(new StandardMBean(new Example2(), Example2MBean.class), example2b);

        // Type definitions checked with http://localhost:22332/jolokia/list/jolokia/test=TypeHelperTest,*?openTypes=true

        // What's the point of the above analysis? If we want:
        //  - TabularTypes other than the ones from MXBeans (key+value items)
        //  - TabularTypes with index using more items
        //  - CompositeTypes with "key" item of non-String type (TabularType from MXBean still can use "key" item of non-String type - if the Map has non-String keys)
        // there's only one way - implement DynamicMBean and return such type in getMBeanInfo(). Period.

        // MXBeans always return values of OpenTypes and should always be supported by Jolokia in a generic way

        for (MBeanServerConnection jmx : new MBeanServerConnection[] { platform, connector.getMBeanServerConnection() }) {
            Object user = jmx.getAttribute(example1a, "User");
            assertTrue(user instanceof CompositeData);
            assertTrue(((CompositeData) user).containsKey("name"));
            assertTrue(((CompositeData) user).containsKey("address"));
            assertTrue(((CompositeData) user).get("address") instanceof CompositeData);
            assertTrue(((CompositeData)((CompositeData) user).get("address")).containsKey("city"));
            assertTrue(((CompositeData)((CompositeData) user).get("address")).containsKey("zip"));
            assertTrue(jmx.getAttribute(example1a, "Users") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example1a, "List") instanceof String[]);
            assertTrue(jmx.getAttribute(example1a, "Lists") instanceof String[][]);
            assertTrue(jmx.getAttribute(example1a, "Set") instanceof Short[]);
            assertTrue(jmx.getAttribute(example1a, "Sets") instanceof Short[][]);

            // never a primitive
            assertTrue(jmx.getAttribute(example1a, "PrimitiveShort") instanceof Short);
            assertTrue(jmx.getAttribute(example1a, "Short") instanceof Short);
            // primitive and wrapped
            assertTrue(jmx.getAttribute(example1a, "PrimitiveShortArray") instanceof short[]);
            assertTrue(jmx.getAttribute(example1a, "ShortArray") instanceof Short[]);
            assertTrue(jmx.getAttribute(example1a, "PrimitiveShort2DArray") instanceof short[][]);
            assertTrue(jmx.getAttribute(example1a, "Short2DArray") instanceof Short[][]);

            assertTrue(jmx.getAttribute(example1a, "ProperTabularType") instanceof TabularData);
            assertTrue(jmx.getAttribute(example1a, "ProperTabularTypeArray") instanceof TabularData[]);
            assertTrue(jmx.getAttribute(example1a, "AlmostProperTabularType") instanceof TabularData);
            assertTrue(jmx.getAttribute(example1a, "ComplexKeyedTabularType") instanceof TabularData);

            // CompositeData type is treated as "bean", so is returned as _different_ CompositeData

            Object compositeData1a = jmx.getAttribute(example1a, "CompositeData");
            assertTrue(compositeData1a instanceof CompositeData);
            assertTrue(((CompositeData) compositeData1a).containsKey("compositeType"));

            compositeData1a = jmx.getAttribute(example1a, "CompositeMXData");
            assertTrue(compositeData1a instanceof CompositeData);
            assertTrue(((CompositeData) compositeData1a).containsKey("compositeType"));

            assertTrue(jmx.getAttribute(example1a, "CompositeDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example1a, "CompositeMXDataArray") instanceof CompositeData[]);

            // TabularData type is treated as "bean", so is actually returned as CompositeData from MXBean

            Object tabularData1a = jmx.getAttribute(example1a, "TabularData");
            assertTrue(tabularData1a instanceof CompositeData);
            assertTrue(((CompositeData)tabularData1a).containsKey("tabularType"));
            assertTrue(((CompositeData)tabularData1a).containsKey("empty"));

            tabularData1a = jmx.getAttribute(example1a, "TabularMXData");
            assertTrue(tabularData1a instanceof CompositeData);
            assertTrue(((CompositeData)tabularData1a).containsKey("tabularType"));
            assertTrue(((CompositeData)tabularData1a).containsKey("empty"));

            assertTrue(jmx.getAttribute(example1a, "TabularDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example1a, "TabularMXDataArray") instanceof CompositeData[]);
        }

        // non-MXBeans may require classloading and are using JSON serialization in Jolokia Client.
        // Jolokia JMX Connector (MBeanServerConnection implementation) may not handle all the types correctly,
        // but I'll try

        for (MBeanServerConnection jmx : new MBeanServerConnection[] { platform, connector.getMBeanServerConnection() }) {
            assertTrue(jmx.getAttribute(example1b, "List") instanceof List);
            assertTrue(jmx.getAttribute(example2a, "List") instanceof List);
            assertTrue(jmx.getAttribute(example2b, "List") instanceof List);
            assertTrue(jmx.getAttribute(example1b, "Lists") instanceof List[]);
            assertTrue(jmx.getAttribute(example2a, "Lists") instanceof List[]);
            assertTrue(jmx.getAttribute(example2b, "Lists") instanceof List[]);
            assertTrue(jmx.getAttribute(example1b, "Set") instanceof Set);
            assertTrue(jmx.getAttribute(example2a, "Set") instanceof Set);
            assertTrue(jmx.getAttribute(example2b, "Set") instanceof Set);
            assertTrue(jmx.getAttribute(example1b, "Sets") instanceof Set[]);
            assertTrue(jmx.getAttribute(example2a, "Sets") instanceof Set[]);
            assertTrue(jmx.getAttribute(example2b, "Sets") instanceof Set[]);

            // never a primitive
            assertTrue(jmx.getAttribute(example1b, "PrimitiveShort") instanceof Short);
            assertTrue(jmx.getAttribute(example2a, "PrimitiveShort") instanceof Short);
            assertTrue(jmx.getAttribute(example2b, "PrimitiveShort") instanceof Short);
            assertTrue(jmx.getAttribute(example1b, "Short") instanceof Short);
            assertTrue(jmx.getAttribute(example2a, "Short") instanceof Short);
            assertTrue(jmx.getAttribute(example2b, "Short") instanceof Short);
            // primitive and wrapped
            assertTrue(jmx.getAttribute(example1b, "PrimitiveShortArray") instanceof short[]);
            assertTrue(jmx.getAttribute(example2a, "PrimitiveShortArray") instanceof short[]);
            assertTrue(jmx.getAttribute(example2b, "PrimitiveShortArray") instanceof short[]);
            assertTrue(jmx.getAttribute(example1b, "ShortArray") instanceof Short[]);
            assertTrue(jmx.getAttribute(example2a, "ShortArray") instanceof Short[]);
            assertTrue(jmx.getAttribute(example2b, "ShortArray") instanceof Short[]);
            assertTrue(jmx.getAttribute(example1b, "PrimitiveShort2DArray") instanceof short[][]);
            assertTrue(jmx.getAttribute(example2a, "PrimitiveShort2DArray") instanceof short[][]);
            assertTrue(jmx.getAttribute(example2b, "PrimitiveShort2DArray") instanceof short[][]);
            assertTrue(jmx.getAttribute(example1b, "Short2DArray") instanceof Short[][]);
            assertTrue(jmx.getAttribute(example2a, "Short2DArray") instanceof Short[][]);
            assertTrue(jmx.getAttribute(example2b, "Short2DArray") instanceof Short[][]);

            assertTrue(jmx.getAttribute(example1b, "ProperTabularType") instanceof Map);
            assertTrue(jmx.getAttribute(example2a, "ProperTabularType") instanceof Map);
            assertTrue(jmx.getAttribute(example2b, "ProperTabularType") instanceof Map);
            assertTrue(jmx.getAttribute(example1b, "ProperTabularTypeArray") instanceof Map[]);
            assertTrue(jmx.getAttribute(example2a, "ProperTabularTypeArray") instanceof Map[]);
            assertTrue(jmx.getAttribute(example2b, "ProperTabularTypeArray") instanceof Map[]);
            assertTrue(jmx.getAttribute(example1b, "AlmostProperTabularType") instanceof Map);
            assertTrue(jmx.getAttribute(example2a, "AlmostProperTabularType") instanceof Map);
            assertTrue(jmx.getAttribute(example2b, "AlmostProperTabularType") instanceof Map);
            if (jmx != platform) {
                assertThrows(RuntimeMBeanException.class, () -> jmx.getAttribute(example1b, "ComplexKeyedTabularType"));
                assertThrows(RuntimeMBeanException.class, () -> jmx.getAttribute(example2a, "ComplexKeyedTabularType"));
                assertThrows(RuntimeMBeanException.class, () -> jmx.getAttribute(example2b, "ComplexKeyedTabularType"));
            }

            // CompositeData type is treated as "bean", so is returned as _different_ CompositeData

            Object compositeData1b = jmx.getAttribute(example1b, "CompositeData");
            assertTrue(compositeData1b instanceof CompositeData);
            assertTrue(((CompositeData) compositeData1b).containsKey("firstName"));
            assertTrue(((CompositeData) compositeData1b).containsKey("lastName"));
            assertTrue(((CompositeData) compositeData1b).containsKey("address"));
            Object compositeData2a = jmx.getAttribute(example2a, "CompositeData");
            assertTrue(compositeData2a instanceof CompositeData);
            assertTrue(((CompositeData) compositeData2a).containsKey("firstName"));
            assertTrue(((CompositeData) compositeData2a).containsKey("lastName"));
            assertTrue(((CompositeData) compositeData2a).containsKey("address"));
            Object compositeData2b = jmx.getAttribute(example2b, "CompositeData");
            assertTrue(compositeData2b instanceof CompositeData);
            assertTrue(((CompositeData) compositeData2b).containsKey("firstName"));
            assertTrue(((CompositeData) compositeData2b).containsKey("lastName"));
            assertTrue(((CompositeData) compositeData2b).containsKey("address"));

            compositeData1b = jmx.getAttribute(example1b, "CompositeMXData");
            assertTrue(compositeData1b instanceof CompositeData);
            assertTrue(((CompositeData) compositeData1b).containsKey("key"));
            assertTrue(((CompositeData) compositeData1b).containsKey("value"));
            compositeData2a = jmx.getAttribute(example2a, "CompositeMXData");
            assertTrue(compositeData2a instanceof CompositeData);
            assertTrue(((CompositeData) compositeData2a).containsKey("key"));
            assertTrue(((CompositeData) compositeData2a).containsKey("value"));
            compositeData2b = jmx.getAttribute(example2b, "CompositeMXData");
            assertTrue(compositeData2b instanceof CompositeData);
            assertTrue(((CompositeData) compositeData2b).containsKey("key"));
            assertTrue(((CompositeData) compositeData2b).containsKey("value"));

            assertTrue(jmx.getAttribute(example1b, "CompositeDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example2a, "CompositeDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example2b, "CompositeDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example1b, "CompositeMXDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example2a, "CompositeMXDataArray") instanceof CompositeData[]);
            assertTrue(jmx.getAttribute(example2b, "CompositeMXDataArray") instanceof CompositeData[]);

            // TabularData type is treated as "bean", so is actually returned as CompositeData from MXBean

            Object tabularData1b = jmx.getAttribute(example1b, "TabularData");
            assertTrue(tabularData1b instanceof TabularData);
            assertTrue(((TabularData)tabularData1b).getTabularType().getRowType().containsKey("firstName"));
            assertTrue(((TabularData)tabularData1b).getTabularType().getRowType().containsKey("lastName"));
            assertTrue(((TabularData)tabularData1b).getTabularType().getRowType().containsKey("address"));
            Object tabularData2a = jmx.getAttribute(example2a, "TabularData");
            assertTrue(tabularData2a instanceof TabularData);
            assertTrue(((TabularData)tabularData2a).getTabularType().getRowType().containsKey("firstName"));
            assertTrue(((TabularData)tabularData2a).getTabularType().getRowType().containsKey("lastName"));
            assertTrue(((TabularData)tabularData2a).getTabularType().getRowType().containsKey("address"));
            Object tabularData2b = jmx.getAttribute(example2b, "TabularData");
            assertTrue(tabularData2b instanceof TabularData);
            assertTrue(((TabularData)tabularData2b).getTabularType().getRowType().containsKey("firstName"));
            assertTrue(((TabularData)tabularData2b).getTabularType().getRowType().containsKey("lastName"));
            assertTrue(((TabularData)tabularData2b).getTabularType().getRowType().containsKey("address"));

            tabularData1b = jmx.getAttribute(example1b, "TabularMXData");
            assertTrue(tabularData1b instanceof TabularData);
            assertTrue(((TabularData)tabularData1b).getTabularType().getRowType().containsKey("key"));
            assertTrue(((TabularData)tabularData1b).getTabularType().getRowType().containsKey("value"));
            tabularData2a = jmx.getAttribute(example2a, "TabularMXData");
            assertTrue(tabularData2a instanceof TabularData);
            assertTrue(((TabularData)tabularData2a).getTabularType().getRowType().containsKey("key"));
            assertTrue(((TabularData)tabularData2a).getTabularType().getRowType().containsKey("value"));
            tabularData2b = jmx.getAttribute(example2b, "TabularMXData");
            assertTrue(tabularData2b instanceof TabularData);
            assertTrue(((TabularData)tabularData2b).getTabularType().getRowType().containsKey("key"));
            assertTrue(((TabularData)tabularData2b).getTabularType().getRowType().containsKey("value"));

            assertTrue(jmx.getAttribute(example1b, "TabularDataArray") instanceof TabularData[]);
            assertTrue(jmx.getAttribute(example2a, "TabularDataArray") instanceof TabularData[]);
            assertTrue(jmx.getAttribute(example2b, "TabularDataArray") instanceof TabularData[]);
            assertTrue(jmx.getAttribute(example1b, "TabularMXDataArray") instanceof TabularData[]);
            assertTrue(jmx.getAttribute(example2a, "TabularMXDataArray") instanceof TabularData[]);
            assertTrue(jmx.getAttribute(example2b, "TabularMXDataArray") instanceof TabularData[]);

//            assertTrue(jmx.getAttribute(example1b, "User") instanceof User);
//            assertTrue(jmx.getAttribute(example2a, "User") instanceof User);
//            assertTrue(jmx.getAttribute(example2b, "User") instanceof User);
//            assertTrue(jmx.getAttribute(example1b, "Users") instanceof User[]);
//            assertTrue(jmx.getAttribute(example2a, "Users") instanceof User[]);
//            assertTrue(jmx.getAttribute(example2b, "Users") instanceof User[]);
        }

        System.out.println();
    }

    @Test
    public void convertCompositeTypeWithoutType() throws Exception {
        RemoteJmxAdapter adapter = (RemoteJmxAdapter) connector.getMBeanServerConnection();
        JSONObject json = new  JSONObject();
        json.put("a", (short) 1);
        json.put("b", (short) 2);
        JSONObject inner = new  JSONObject();
        json.put("c", inner);
        inner.put("k", "v");
        CompositeData data = (CompositeData) adapter.convertValue("convertCompositeTypeWithoutType", CompositeData.class.getName(), null, json);
        assertEquals(data.getCompositeType().getTypeName(), JSONObject.class.getName());
        assertEquals(data.getCompositeType().getType("a"), SimpleType.SHORT);
        assertEquals(data.getCompositeType().getType("b"), SimpleType.SHORT);
        assertTrue(data.getCompositeType().getType("c") instanceof CompositeType);
        assertEquals(data.get("a"), (short) 1);
        assertEquals(data.get("b"), (short) 2);
        assertEquals(((CompositeData) data.get("c")).get("k"), "v");
    }

    @Test
    public void convertCompositeTypeWithoutTypeButKnown() throws Exception {
        RemoteJmxAdapter adapter = (RemoteJmxAdapter) connector.getMBeanServerConnection();
        JSONObject json = new  JSONObject();
        json.put("init", 1L);
        json.put("committed", 2L);
        json.put("usage", 3L);
        json.put("max", 4L);
        CompositeData data = (CompositeData) adapter.convertValue("convertCompositeTypeWithoutTypeButKnown", CompositeData.class.getName(), null, json);
        assertEquals(data.getCompositeType().getTypeName(), MemoryUsage.class.getName());
    }

    @Test
    public void convertCompositeTypeWithoutTypeButKnownInternal() throws Exception {
        RemoteJmxAdapter adapter = (RemoteJmxAdapter) connector.getMBeanServerConnection();
        JSONObject json = new  JSONObject();
        json.put("name", "n");
        json.put("origin", "o");
        json.put("value", "v");
        json.put("writeable", false);
        CompositeData data = (CompositeData) adapter.convertValue("convertCompositeTypeWithoutTypeButKnownInternal1", CompositeData.class.getName(), null, json);
        assertEquals(data.getCompositeType().getTypeName(), "com.sun.management.VMOption");

        json.clear();
        json.put("name", "n");
        json.put("origin", "o");
        json.put("value", "v");
        data = (CompositeData) adapter.convertValue("convertCompositeTypeWithoutTypeButKnownInternal2", CompositeData.class.getName(), null, json);
        assertEquals(data.getCompositeType().getTypeName(), JSONObject.class.getName());
    }

    @Test
    public void convertFullTabularData() throws Exception {
        RemoteJmxAdapter adapter = (RemoteJmxAdapter) connector.getMBeanServerConnection();
        JSONObject json = new JSONObject();
        JSONArray index = new JSONArray();
        index.add("a1");
        index.add("a2");
        json.put("indexNames", index);
        JSONArray values = new JSONArray();
        JSONObject v1 = new JSONObject();
        v1.put("a1", "v11");
        v1.put("a2", "v2");
        v1.put("v1", "v31");
        v1.put("v2", "v4");
        values.add(v1);
        JSONObject v2 = new JSONObject();
        v2.put("a1", "v12");
        v2.put("a2", "v2");
        v2.put("v1", "v32");
        v2.put("v2", "v4");
        values.add(v2);
        json.put("values", values);

        TabularData data = (TabularData) adapter.convertValue("convertFullTabularData", TabularData.class.getName(), null, json);
        assertEquals(data.getTabularType().getRowType().getTypeName(), JSONObject.class.getName());
        assertEquals(data.getTabularType().getIndexNames(), List.of("a1", "a2"));
        assertEquals(data.size(), 2);
        assertEquals(data.get(new String[] { "v11", "v2" }).get("v1"), "v31");
        assertEquals(data.get(new String[] { "v12", "v2" }).get("v1"), "v32");
    }

    @Test
    public void convertNestedTabularData() throws Exception {
        RemoteJmxAdapter adapter = (RemoteJmxAdapter) connector.getMBeanServerConnection();
        String jsonData = """
                {
                  "id2": {
                    "id2": {
                      "2": {
                        "key1": "id2",
                        "key2": "id2",
                        "value2": "some value",
                        "value1": 42,
                        "key3": 2
                      }
                    }
                  },
                  "id1": {
                    "id2": {
                      "1": {
                        "key1": "id1",
                        "key2": "id2",
                        "value2": "some value",
                        "value1": 42,
                        "key3": 1
                      },
                      "2": {
                        "key1": "id1",
                        "key2": "id2",
                        "value2": "some value",
                        "value1": 43,
                        "key3": 2
                      }
                    }
                  }
                }
                """;
        JSONObject json = new JSONParser().parse(jsonData, JSONObject.class);

        TabularData data = (TabularData) adapter.convertValue("convertNestedTabularData", TabularData.class.getName(), null, json);
        assertEquals(data.getTabularType().getRowType().getTypeName(), JSONObject.class.getName());
        assertEquals(data.getTabularType().getIndexNames(), List.of("key1", "key2", "key3"));
        assertEquals(data.size(), 3);
        assertEquals(data.get(new Object[] { "id2", "id2", 2L }).get("key2"), "id2");
        assertEquals(data.get(new Object[] { "id1", "id2", 2L }).get("value1"), 43L);
    }

    @Test
    public void convertMXTabularData() throws Exception {
        RemoteJmxAdapter adapter = (RemoteJmxAdapter) connector.getMBeanServerConnection();
        String jsonData = """
                {
                  "k1": {
                    "k1a": 1,
                    "k1b": 3
                  },
                  "k2": {
                    "k2b": 4,
                    "k2a": 2
                  }
                }
                """;
        JSONObject json = new JSONParser().parse(jsonData, JSONObject.class);

        TabularData data = (TabularData) adapter.convertValue("convertMXTabularData", TabularData.class.getName(), null, json);
        assertEquals(data.getTabularType().getRowType().getTypeName(), "java.util.Map<java.lang.String, java.util.Map<java.lang.String, java.lang.Long>>");
        assertEquals(data.getTabularType().getIndexNames(), List.of("key"));
        assertEquals(data.size(), 2);
        assertEquals(((TabularData)data.get(new Object[] { "k1" }).get("value")).get(new Object[] { "k1a" }).get("value"), 1L);
        assertEquals(((TabularData)data.get(new Object[] { "k2" }).get("value")).get(new Object[] { "k2b" }).get("value"), 4L);
    }

    @Test
    public void illegalMXBeans() throws Exception {
        ObjectName name = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest3");
        try {
            platform.registerMBean(new SimpleIllegal1(), name);
            fail("Should fail with \"Class java.net.InetAddress has method name clash: isLoopbackAddress, getLoopbackAddress\"");
        } catch (NotCompliantMBeanException expected) {
        }
        try {
            platform.registerMBean(new SimpleIllegal2(), name);
            fail("Should fail with \"Recursive data structure, including java.io.File\"");
        } catch (NotCompliantMBeanException expected) {
        }

        // even if StringBuilder is not a proper OpenType, according to @MXBean
        // (https://docs.oracle.com/en/java/javase/17/docs/api/java.management/javax/management/MXBean.html) an
        // Object is _convertible_ if it has at least one getter - it is then mapped to CompositeType
        platform.registerMBean(new SimpleIllegal3(), name);
        MBeanInfo info = platform.getMBeanInfo(name);
        MBeanAttributeInfo attrInfo = info.getAttributes()[0];
        if (attrInfo instanceof OpenMBeanAttributeInfo openAttrInfo) {
            if (openAttrInfo.getOpenType() instanceof TabularType tabularType) {
                CompositeType rowType = tabularType.getRowType();
                // Mappings for maps (Map<K,V> etc) - expected two items: "key" and "value"
                assertEquals(rowType.keySet().size(), 2);
                OpenType<?> rowValueType = rowType.getType("value");
                if (rowValueType instanceof CompositeType entryType) {
                    assertEquals(entryType.keySet().size(), 1);
                    OpenType<?> singleGetterType = entryType.getType("empty");
                    if (singleGetterType instanceof SimpleType<?>) {
                        assertEquals(singleGetterType, SimpleType.BOOLEAN);
                    } else {
                        fail("Expected SimpleType for \"empty\" property of StringBuilder");
                    }
                } else {
                    fail("Expected CompositeType for mapping of StringBuilder");
                }
            } else {
                fail("Expected TabularType for \"Mapping\" attribute");
            }
        } else {
            fail("Expected OpenMBeanAttributeInfo");
        }
    }

    @Test
    public void customComposites() throws Exception {
        ObjectName name = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest4");
        platform.registerMBean(new Custom(), name);
        MBeanInfo info = platform.getMBeanInfo(name);
        assertTrue(info.getAttributes()[0] instanceof OpenMBeanAttributeInfo);
    }

    @Test(expectedExceptions = NotCompliantMBeanException.class)
    public void customRecursiveComposites() throws Exception {
        ObjectName name = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest5");
        // no we can't have recursive MXBeans with OpenTypes
        platform.registerMBean(new Custom2(), name);
    }

    @Test
    public void dynamicTabularType() throws Exception {
        MBeanInfo platformInfo = platform.getMBeanInfo(customDynamicName);
        MBeanInfo jolokiaInfo = connector.getMBeanServerConnection().getMBeanInfo(customDynamicName);
        if (useOpenTypeInformation()) {
            assertEquals(jolokiaInfo, platformInfo);
        } else {
            assertEquals(jolokiaInfo.getAttributes()[0].getName(), platformInfo.getAttributes()[0].getName());
            assertEquals(jolokiaInfo.getAttributes()[0].getType(), platformInfo.getAttributes()[0].getType());
        }

        TabularData platformValue = (TabularData) platform.getAttribute(customDynamicName, "CustomTabularData");
        TabularData jolokiaValue = (TabularData) connector.getMBeanServerConnection().getAttribute(customDynamicName, "CustomTabularData");
        if (useOpenTypeInformation()) {
            assertEquals(jolokiaValue, platformValue);
        } else {
            assertEquals(platformValue.get(new Object[] { (byte) 66, 42424242L }).get("id1"), (byte) 66);
            assertEquals(jolokiaValue.get(new Object[] { 67L, 42424242L }).get("id1"), 67L);
        }
    }

    @Test
    public void getMBeanInfoForKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.lang:type=Memory");
        MBeanInfo jolokiaInfo = connector.getMBeanServerConnection().getMBeanInfo(cl);
        MBeanInfo platformInfo = platform.getMBeanInfo(cl);

        Object usage1 = platform.getAttribute(cl, "HeapMemoryUsage");
        Object usage2 = connector.getMBeanServerConnection().getAttribute(cl, "HeapMemoryUsage");
        // previously Martin was "recursively" building types by checking some hardcoded data
        // he used javax.management.openmbean.SimpleType.isValue() for example - good to remember!
        // of course it fails if there's some missing data (optional fields in CompositeData or no rows in TabularData)
        assertNotNull(usage1);
        assertNotNull(usage2);

        // we can almost do this:
        //assertEquals(info2, info1);
        // but the issue is with the Descriptor fields which are not reconstructed 1:1
        assertEquals(jolokiaInfo.getClassName(), platformInfo.getClassName());
        assertEquals(jolokiaInfo.getDescription(), platformInfo.getDescription());
        assertEquals(jolokiaInfo.getAttributes().length, platformInfo.getAttributes().length);

        Map<String, MBeanAttributeInfo> m1 = new TreeMap<>();
        Map<String, MBeanAttributeInfo> m2 = new TreeMap<>();
        for (int a = 0; a < jolokiaInfo.getAttributes().length; a++) {
            m1.put(jolokiaInfo.getAttributes()[a].getName(), jolokiaInfo.getAttributes()[a]);
            m2.put(platformInfo.getAttributes()[a].getName(), platformInfo.getAttributes()[a]);
        }
        for (String a : m1.keySet()) {
            MBeanAttributeInfo aInfo = m1.get(a);
            MBeanAttributeInfo platformAInfo = m2.get(a);
            assertEquals(aInfo.getName(), platformAInfo.getName());
            assertEquals(aInfo.getType(), platformAInfo.getType());
            assertEquals(aInfo.getDescription(), platformAInfo.getDescription());
        }
    }

    @Test
    public void getStringAttributeFromKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.lang:type=Runtime");
        Object vmName = connector.getMBeanServerConnection().getAttribute(cl, "VmName");
        assertTrue(vmName instanceof String);
    }

    @Test(expectedExceptions = AttributeNotFoundException.class, expectedExceptionsMessageRegExp = "No such attribute: Jolokia")
    public void getUnknownAttributeFromKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.lang:type=Runtime");
        Object vmName = connector.getMBeanServerConnection().getAttribute(cl, "Jolokia");
        assertTrue(vmName instanceof String);
    }

    @Test
    public void getKnownAndUnknownAttributeFromKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.lang:type=Runtime");
        AttributeList list = connector.getMBeanServerConnection().getAttributes(cl, new String[] { "Jolokia", "SpecVersion" });
        assertFalse(list.isEmpty());
        assertEquals(list.size(), 1);
        assertTrue(list.get(0) instanceof Attribute);
        assertEquals(((Attribute) list.get(0)).getName(), "SpecVersion");
        assertNotNull(((Attribute) list.get(0)).getValue());
    }

    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void getAttributeForPatternMBeanFromPlatformServer() throws Exception {
        // yes - that's how it should work. Jolokia client returns a nice map
        ObjectName cl = ObjectName.getInstance("java.lang:type=MemoryPool,*");
        platform.getAttribute(cl, "Name");
    }

    @Test
    public void getBooleanAttributeFromKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.lang:type=Memory");
        Object verbose = connector.getMBeanServerConnection().getAttribute(cl, "Verbose");
        assertTrue(verbose instanceof Boolean);
    }

    @Test
    public void getNumericAttributeFromKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.lang:type=Runtime");
        Object startTime = connector.getMBeanServerConnection().getAttribute(cl, "StartTime");
        assertTrue(startTime instanceof Number);
    }

    @Test
    public void getStringArrayAttributeFromKnownMBean() throws Exception {
        ObjectName cl = ObjectName.getInstance("java.util.logging:type=Logging");
        Object loggerNames = connector.getMBeanServerConnection().getAttribute(cl, "LoggerNames");
        assertTrue(loggerNames instanceof String[]);
        assertTrue(((String[]) loggerNames).length > 0);
    }

    @Test
    public void firstGetAttributesCallFromJConsole() throws Exception {
        // when debugging JConsole with Jolokia adapter, I found this call to be the first (LoadedClassCount attr)
        ObjectName cl = ObjectName.getInstance("java.lang:type=ClassLoading");
        AttributeList attributes = connector.getMBeanServerConnection().getAttributes(cl, new String[]{"LoadedClassCount", "Fake"});
        assertEquals(attributes.size(), 1);
        assertTrue(((Attribute) attributes.get(0)).getValue() instanceof Number);

        Object value = connector.getMBeanServerConnection().getAttribute(cl, "LoadedClassCount");
        assertTrue(value instanceof Number);
    }

    @Test
    public void setSingleMXBeanRWAttribute() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable1");
        platform.registerMBean(bean, name);

        connector.getMBeanServerConnection().setAttribute(name, new Attribute("StringValue", "Hello"));
        assertEquals(bean.getStringValue(), "Hello");

        platform.unregisterMBean(name);
    }

    @Test
    public void setSingleMXBeanWriteOnlyAttribute() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable2");
        platform.registerMBean(bean, name);

        connector.getMBeanServerConnection().setAttribute(name, new Attribute("BigIntValue", BigInteger.TEN));
        assertEquals(bean.bigIntValue, BigInteger.TEN);

        platform.unregisterMBean(name);
    }

    @Test
    public void setTabularAttribute() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable3");
        platform.registerMBean(bean, name);

        MBeanInfo info = connector.getMBeanServerConnection().getMBeanInfo(name);
        MBeanAttributeInfo[] attrs = info.getAttributes();
        MBeanAttributeInfo attrInfo = Arrays.stream(attrs).filter(a -> a.getName().equals("Mapping")).findFirst().orElseThrow(AttributeNotFoundException::new);
        if (useOpenTypeInformation()) {
            assertTrue(attrInfo instanceof OpenMBeanAttributeInfo);
            OpenType<?> type = ((OpenMBeanAttributeInfo) attrInfo).getOpenType();
            assertTrue(type instanceof TabularType);

            TabularData td = new TabularDataSupport((TabularType) type);
            td.put(new CompositeDataSupport(td.getTabularType().getRowType(), Map.of(
                "key", "item1",
                "value", ObjectName.getInstance(ManagementFactory.MEMORY_MXBEAN_NAME)
            )));
            td.put(new CompositeDataSupport(td.getTabularType().getRowType(), Map.of(
                "key", "item2",
                "value", ObjectName.getInstance(ManagementFactory.RUNTIME_MXBEAN_NAME)
            )));
            connector.getMBeanServerConnection().setAttribute(name, new Attribute("Mapping", td));
            assertEquals(bean.getMapping().get("item2").getCanonicalName(), ManagementFactory.RUNTIME_MXBEAN_NAME);
        }

        platform.unregisterMBean(name);
    }

    @Test
    public void setTabularAttributeViaMXBeanProxy() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable4");
        platform.registerMBean(bean, name);

        Settable1MXBean settable = JMX.newMXBeanProxy(connector.getMBeanServerConnection(), name, Settable1MXBean.class);

        settable.setMapping(Map.of(
            "item1", ObjectName.getInstance(ManagementFactory.MEMORY_MXBEAN_NAME),
            "item2", ObjectName.getInstance(ManagementFactory.RUNTIME_MXBEAN_NAME)
        ));

        assertEquals(bean.getMapping().get("item2").getCanonicalName(), ManagementFactory.RUNTIME_MXBEAN_NAME);

        platform.unregisterMBean(name);
    }

    @Test
    public void setUnknownMXBeanAttribute() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable5");
        platform.registerMBean(bean, name);

        try {
            connector.getMBeanServerConnection().setAttribute(name, new Attribute("Ha", "Ho"));
        } catch (AttributeNotFoundException expected) {
        }

        platform.unregisterMBean(name);
    }

    @Test
    public void setMultipleMXBeanAttributes() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable6");
        platform.registerMBean(bean, name);

        AttributeList list = new AttributeList();
        list.add(new Attribute("StringValue", "Hello"));
        list.add(new Attribute("BigIntValue", BigInteger.TEN));
        list = connector.getMBeanServerConnection().setAttributes(name, list);
        assertEquals(bean.getStringValue(), "Hello");
        assertEquals(bean.bigIntValue, BigInteger.TEN);

        // 2, because everything should be fine
        assertEquals(list.size(), 2);

        platform.unregisterMBean(name);
    }

    @Test
    public void setProperAndUnknownMXBeanAttributes() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable7");
        platform.registerMBean(bean, name);

        AttributeList list = new AttributeList();
        list.add(new Attribute("StringValue", "Hello"));
        list.add(new Attribute("Ha", "Ho"));
        list = connector.getMBeanServerConnection().setAttributes(name, list);
        assertEquals(bean.getStringValue(), "Hello");

        // 2, because everything should be fine
        assertEquals(list.size(), 1);

        platform.unregisterMBean(name);
    }

    @Test
    public void invokeKnownMXBean() throws Exception {
        ObjectName threading = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
        long id = Thread.currentThread().getId();

        // proxy access
        ThreadMXBean proxy = JMX.newMXBeanProxy(connector.getMBeanServerConnection(), threading, ThreadMXBean.class);
        ThreadInfo info = proxy.getThreadInfo(id);
        assertEquals(info.getThreadName(), Thread.currentThread().getName());

        // OpenType access
        Object info2 = connector.getMBeanServerConnection().invoke(threading, "getThreadInfo", new Object[]{id}, new String[]{"long"});
        if (info2 instanceof CompositeData cd) {
            assertEquals(cd.get("threadName"), Thread.currentThread().getName());
        } else {
            fail("Expected CompositedData, got " + info2.getClass().getName());
        }
    }

}
