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
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
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
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
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
import org.jolokia.client.jmxadapter.beans.ManyTypes;
import org.jolokia.client.jmxadapter.beans.Mixed;
import org.jolokia.client.jmxadapter.beans.Settable1;
import org.jolokia.client.jmxadapter.beans.Settable1MXBean;
import org.jolokia.client.jmxadapter.beans.SimpleIllegal1;
import org.jolokia.client.jmxadapter.beans.SimpleIllegal2;
import org.jolokia.client.jmxadapter.beans.SimpleIllegal3;
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

    JMXConnector connector;

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

        jfr = ObjectName.getInstance("jdk.management.jfr:type=FlightRecorder");

        allOpenTypesName = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest1");
        platform.registerMBean(new AllOpenTypes(), allOpenTypesName);

        manyTypesName = ObjectName.getInstance("jolokia:test=JolokiaJmxConnectorTest2");
        platform.registerMBean(new ManyTypes(), manyTypesName);

        mixedName = ObjectName.getInstance("jolokia:test=Mixed>");
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
        connector = JMXConnectorFactory.connect(serviceURL);
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
        assertEquals(jolokiaInfo, platformInfo);

        Object platformValue = platform.getAttribute(customDynamicName, "CustomTabularData");
        Object jolokiaValue = connector.getMBeanServerConnection().getAttribute(customDynamicName, "CustomTabularData");
        assertEquals(jolokiaValue, platformValue);
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
        ObjectName name = new ObjectName("jolokia:type=Settable1");
        platform.registerMBean(bean, name);

        connector.getMBeanServerConnection().setAttribute(name, new Attribute("BigIntValue", BigInteger.TEN));
        assertEquals(bean.bigIntValue, BigInteger.TEN);

        platform.unregisterMBean(name);
    }

    @Test
    public void setTabularAttribute() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable1");
        platform.registerMBean(bean, name);

        MBeanInfo info = connector.getMBeanServerConnection().getMBeanInfo(name);
        MBeanAttributeInfo[] attrs = info.getAttributes();
        MBeanAttributeInfo attrInfo = Arrays.stream(attrs).filter(a -> a.getName().equals("Mapping")).findFirst().orElseThrow(AttributeNotFoundException::new);
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

        platform.unregisterMBean(name);
    }

    @Test
    public void setTabularAttributeViaMXBeanProxy() throws Exception {
        Settable1 bean = new Settable1();
        ObjectName name = new ObjectName("jolokia:type=Settable1");
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
        ObjectName name = new ObjectName("jolokia:type=Settable1");
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
        ObjectName name = new ObjectName("jolokia:type=Settable1");
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
        ObjectName name = new ObjectName("jolokia:type=Settable1");
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
