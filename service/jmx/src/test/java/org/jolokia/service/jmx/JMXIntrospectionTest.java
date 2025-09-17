/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.service.jmx;

import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.jolokia.service.jmx.management.Bad1;
import org.jolokia.service.jmx.management.Bad2;
import org.jolokia.service.jmx.management.MapAttribute4;
import org.jolokia.service.jmx.management.MapAttribute5;
import org.jolokia.service.jmx.management.SingleDateAttribute1;
import org.jolokia.service.jmx.management.SingleDateAttribute2;
import org.jolokia.service.jmx.management.SingleFileAttribute3;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Test for checking various MBean attribute/parameter/returnValue types - can these be introspected? Can
 * we support perfect conversion to/from these types? How to ensure that we can (de)serialize JSON data into
 * {@link javax.management.openmbean.OpenType} values (like {@link javax.management.openmbean.CompositeData})
 * or into domain values which support {@link javax.management.MXBean} specification (like automatic conversion
 * to {@link javax.management.openmbean.OpenType}?)
 */
public class JMXIntrospectionTest {

    private static MBeanServer mbs;

    @BeforeClass
    public static void beforeAll() {
        mbs = ManagementFactory.getPlatformMBeanServer();
    }

    @Test
    public void mBeanWithNonSerializableAttribute() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        mbs.registerMBean(new Bad1(), name);

        // MBean with non-serializable attributes/operation parameters/return values still passes
        // com.sun.jmx.mbeanserver.Introspector.checkCompliance() because Serializable is not a requirement
        // of "2.2 Standard MBeans" chapter
        Object v = mbs.getAttribute(name, "Reader");
        assertTrue(v instanceof InputStreamReader);

        // this works locally, but remotely we get NotSerializableException here:
        // "RMI TCP Connection(31)-192.168.0.165@3379" daemon prio=9 tid=0x23 nid=NA runnable
        //  java.lang.Thread.State: RUNNABLE
        //     at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1197)
        //     at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1582)
        //     at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1539)
        //     at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1448)
        //     at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1191)
        //     at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:354)
        //     at java.util.ArrayList.writeObject(ArrayList.java:866)
        //     at jdk.internal.reflect.GeneratedMethodAccessor10.invoke(Unknown Source:-1)
        //     at jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        //     at java.lang.reflect.Method.invoke(Method.java:568)
        //     at java.io.ObjectStreamClass.invokeWriteObject(ObjectStreamClass.java:1074)
        //     at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1526)
        //     at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1448)
        //     at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1191)
        //     at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:354)
        //     at sun.rmi.server.UnicastRef.marshalValue(UnicastRef.java:294)
        //     at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:370)
        //     at sun.rmi.transport.Transport$1.run(Transport.java:200)
        //     at sun.rmi.transport.Transport$1.run(Transport.java:197)
        //     at java.security.AccessController.executePrivileged(AccessController.java:807)
        //     at java.security.AccessController.doPrivileged(AccessController.java:712)
        //     at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        //     at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:587)
        //     at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:828)
        //     at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:705)
        //     at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$293/0x00007f138818e4b8.run(Unknown Source:-1)
        //     at java.security.AccessController.executePrivileged(AccessController.java:776)
        //     at java.security.AccessController.doPrivileged(AccessController.java:399)
        //     at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:704)
        //     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
        //     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
        //     at java.lang.Thread.run(Thread.java:842)

        // there's absolutely no conversion involved when dealing with standard MBeans
        v = mbs.getAttribute(name, "UglyMap");
        assertTrue(v instanceof Map);

        mbs.unregisterMBean(name);
    }

    @Test
    public void mBeanWithSerializableAttribute() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        mbs.registerMBean(new SingleDateAttribute1(), name);
        mbs.unregisterMBean(name);
    }

    @Test(expectedExceptions = NotCompliantMBeanException.class)
    public void mxBeanWithMap() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        // should fail with "Class java.net.InetAddress has method name clash: isLoopbackAddress, getLoopbackAddress"
        // at registration time, because ParameterizedType contains InetAddress type variable
        mbs.registerMBean(new MapAttribute4(), name);
    }

    @Test(expectedExceptions = NotCompliantMBeanException.class)
    public void mxBeanWithObjectMap() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        // with Map<Object, Object>, the failure is: "Can't map java.lang.Object to an open data type"
        // with Map<?, ?>, the failure is: "Cannot map type: ?"
        mbs.registerMBean(new MapAttribute5(), name);
    }

    @Test(expectedExceptions = NotCompliantMBeanException.class)
    public void mxBeanWithNonSerializableAttribute() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        mbs.registerMBean(new Bad2(), name);
    }

    @Test
    public void mxBeanWithSerializableOpenTypeAttribute() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        // Date is handled by com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.IdentityMapping
        // because SimpleType.DATE is used
        mbs.registerMBean(new SingleDateAttribute2(), name);
        mbs.unregisterMBean(name);
    }

    @Test(expectedExceptions = NotCompliantMBeanException.class)
    public void mxBeanWithSerializableNonOpenTypeAttribute() throws Exception {
        ObjectName name = new ObjectName("jolokia.test:type=Test");
        // com.sun.jmx.mbeanserver.Introspector.checkCompliance() passes, but
        // com.sun.jmx.mbeanserver.Introspector.makeDynamicMBean() fails because
        // com.sun.jmx.mbeanserver.ConvertingMethod() constructor fails
        //
        // during registration, com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.mappings contains:
        //  - boolean
        //  - char
        //  - byte
        //  - short
        //  - int
        //  - long
        //  - float
        //  - double
        //  - void
        //  - class [Z
        //  - class [C
        //  - class [B
        //  - class [S
        //  - class [I
        //  - class [J
        //  - class [F
        //  - class [D
        //  - class java.lang.Boolean
        //  - class java.lang.Character
        //  - class java.lang.Byte
        //  - class java.lang.Short
        //  - class java.lang.Integer
        //  - class java.lang.Long
        //  - class java.lang.Float
        //  - class java.lang.Double
        //  - class java.lang.Void
        //  - class java.lang.String
        //  - class java.math.BigDecimal
        //  - class java.math.BigInteger
        //  - class java.util.Date
        //  - class javax.management.ObjectName
        //  - class com.sun.management.GcInfo
        //  - class com.sun.management.VMOption$Origin
        //  - class com.sun.management.VMOption
        //  - class java.lang.management.LockInfo
        //  - class java.lang.management.MemoryType
        //  - class java.lang.management.MemoryUsage
        //  - class java.lang.management.MonitorInfo
        //  - class java.lang.management.ThreadInfo
        //  - class java.lang.StackTraceElement
        //  - class java.lang.Thread$State
        //  - class jdk.management.jfr.ConfigurationInfo
        //  - class jdk.management.jfr.EventTypeInfo
        //  - class jdk.management.jfr.RecordingInfo
        //  - class jdk.management.jfr.SettingDescriptorInfo
        //  - class [Ljava.lang.management.LockInfo;
        //  - class [Ljava.lang.management.MonitorInfo;
        //  - class [Ljava.lang.management.ThreadInfo;
        //  - class [Ljava.lang.StackTraceElement;
        //  - class [Ljava.lang.String;
        //  - java.util.List<com.sun.management.VMOption>
        //  - java.util.List<java.lang.String>
        //  - java.util.List<jdk.management.jfr.ConfigurationInfo>
        //  - java.util.List<jdk.management.jfr.EventTypeInfo>
        //  - java.util.List<jdk.management.jfr.RecordingInfo>
        //  - java.util.List<jdk.management.jfr.SettingDescriptorInfo>
        //  - java.util.Map<java.lang.String, java.lang.management.MemoryUsage>
        //  - java.util.Map<java.lang.String, java.lang.String>
        //
        // these are mapped to various implementations of NonNullMXBeanMapping:
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.ArrayMapping: 4
        //     - javaType: java.lang.reflect.Type  =  {@495} "class [Ljava.lang.StackTraceElement;"
        //     - javaType: java.lang.reflect.Type  = {@2938} "class [Ljava.lang.management.ThreadInfo;"
        //     - javaType: java.lang.reflect.Type  = {@2948} "class [Ljava.lang.management.MonitorInfo;"
        //     - javaType: java.lang.reflect.Type  = {@2918} "class [Ljava.lang.management.LockInfo;"
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.CollectionMapping: 6
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2899} "java.util.List<jdk.management.jfr.RecordingInfo>"
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2906} "java.util.List<com.sun.management.VMOption>"
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2941} "java.util.List<java.lang.String>"
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2913} "java.util.List<jdk.management.jfr.ConfigurationInfo>"
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2909} "java.util.List<jdk.management.jfr.SettingDescriptorInfo>"
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2926} "java.util.List<jdk.management.jfr.EventTypeInfo>"
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.CompositeMapping: 11
        //     - javaType: java.lang.reflect.Type  = {@2394} "class java.lang.management.LockInfo"
        //     - javaType: java.lang.reflect.Type  = {@2549} "class com.sun.management.VMOption"
        //     - javaType: java.lang.reflect.Type  = {@2396} "class java.lang.management.MonitorInfo"
        //     - javaType: java.lang.reflect.Type  = {@2393} "class java.lang.management.ThreadInfo"
        //     - javaType: java.lang.reflect.Type  =  {@707} "class java.lang.StackTraceElement"
        //     - javaType: java.lang.reflect.Type  = {@2566} "class jdk.management.jfr.EventTypeInfo"
        //     - javaType: java.lang.reflect.Type  = {@2557} "class jdk.management.jfr.SettingDescriptorInfo"
        //     - javaType: java.lang.reflect.Type  = {@2565} "class jdk.management.jfr.ConfigurationInfo"
        //     - javaType: java.lang.reflect.Type  = {@2564} "class jdk.management.jfr.RecordingInfo"
        //     - javaType: java.lang.reflect.Type  = {@2475} "class java.lang.management.MemoryUsage"
        //     - javaType: java.lang.reflect.Type  = {@2546} "class com.sun.management.GcInfo"
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.EnumMapping: 3
        //     - javaType: java.lang.reflect.Type  = {@2946} "class com.sun.management.VMOption$Origin"
        //     - javaType: java.lang.reflect.Type  = {@2895} "class java.lang.Thread$State"
        //     - javaType: java.lang.reflect.Type  = {@2934} "class java.lang.management.MemoryType"
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.IdentityMapping: 32
        //     - javaType: java.lang.reflect.Type  = {@2929} "boolean"
        //     - javaType: java.lang.reflect.Type  = {@2936} "char"
        //     - javaType: java.lang.reflect.Type  = {@2888} "byte"
        //     - javaType: java.lang.reflect.Type  = {@2916} "short"
        //     - javaType: java.lang.reflect.Type  = {@2944} "int"
        //     - javaType: java.lang.reflect.Type  = {@2932} "long"
        //     - javaType: java.lang.reflect.Type  = {@2873} "float"
        //     - javaType: java.lang.reflect.Type  = {@2884} "double"
        //     - javaType: java.lang.reflect.Type  = {@2920} "void"
        //     - javaType: java.lang.reflect.Type  =  {@420} "class [Z"
        //     - javaType: java.lang.reflect.Type  =  {@419} "class [C"
        //     - javaType: java.lang.reflect.Type  =  {@416} "class [B"
        //     - javaType: java.lang.reflect.Type  =  {@415} "class [S"
        //     - javaType: java.lang.reflect.Type  =  {@414} "class [I"
        //     - javaType: java.lang.reflect.Type  =  {@412} "class [J"
        //     - javaType: java.lang.reflect.Type  =  {@418} "class [F"
        //     - javaType: java.lang.reflect.Type  =  {@417} "class [D"
        //     - javaType: java.lang.reflect.Type  =  {@492} "class java.lang.Boolean"
        //     - javaType: java.lang.reflect.Type  =  {@491} "class java.lang.Character"
        //     - javaType: java.lang.reflect.Type  =  {@485} "class java.lang.Byte"
        //     - javaType: java.lang.reflect.Type  =  {@483} "class java.lang.Short"
        //     - javaType: java.lang.reflect.Type  =  {@481} "class java.lang.Integer"
        //     - javaType: java.lang.reflect.Type  =  {@479} "class java.lang.Long"
        //     - javaType: java.lang.reflect.Type  =  {@487} "class java.lang.Float"
        //     - javaType: java.lang.reflect.Type  =  {@486} "class java.lang.Double"
        //     - javaType: java.lang.reflect.Type  =  {@197} "class java.lang.Void"
        //     - javaType: java.lang.reflect.Type  =  {@614} "class java.lang.String"
        //     - javaType: java.lang.reflect.Type  = {@1962} "class java.math.BigInteger"
        //     - javaType: java.lang.reflect.Type  = {@2893} "class java.math.BigDecimal"
        //     - javaType: java.lang.reflect.Type  = {@1740} "class java.util.Date"
        //     - javaType: java.lang.reflect.Type  = {@2229} "class javax.management.ObjectName"
        //     - javaType: java.lang.reflect.Type  =  {@613} "class [Ljava.lang.String;"
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.MXBeanRefMapping: 0
        //  - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.TabularMapping: 2
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2879} "java.util.Map<java.lang.String, java.lang.String>"
        //     - javaType: java.lang.reflect.Type  = {ParameterizedTypeImpl@2923} "java.util.Map<java.lang.String, java.lang.management.MemoryUsage>"

        // if some mapping is not present, com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.makeMapping() is
        // called, but the type of registered MXBean object should be:
        //  - java.lang.reflect.GenericArrayType
        //     - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.makeArrayOrCollectionMapping() called
        //  - java.lang.Class
        //     - java.lang.Class.isEnum()
        //         - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.makeEnumMapping() called
        //     - java.lang.Class.isArray()
        //         - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.makeArrayOrCollectionMapping() called
        //     - javax.management.JMX.isMXBeanInterface()
        //     - any other
        //        - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.makeCompositeMapping() called
        //           - getters are collected
        //           - com.sun.management.GcInfo is handled in legacy way (to skip its getCompositeType method)
        //           -
        //  - java.lang.reflect.ParameterizedType
        //     - com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.makeParameterizedTypeMapping() called
        //        - explicitly handling ONLY these: List, Set, SortedSet, Map, SortedMap
        //        - list, set, sorted set:
        //           - makeArrayOrCollectionMapping()
        //        - map, sorted map:
        //           - makeTabularMapping()
        //
        // for File we have these attributes when attempting to create a CompositeMapping:
        // getterMap = {java.util.TreeMap@3377}  size = 15
        //   {@3479} "absolute" -> {java.lang.reflect.Method@3390} "public boolean java.io.File.isAbsolute()"
        //   {@3480} "absoluteFile" -> {java.lang.reflect.Method@3404} "public java.io.File java.io.File.getAbsoluteFile()"
        //   {@3481} "absolutePath" -> {java.lang.reflect.Method@3401} "public java.lang.String java.io.File.getAbsolutePath()"
        //   {@3482} "canonicalFile" -> {java.lang.reflect.Method@3406} "public java.io.File java.io.File.getCanonicalFile() throws java.io.IOException"
        //   {@3483} "canonicalPath" -> {java.lang.reflect.Method@3402} "public java.lang.String java.io.File.getCanonicalPath() throws java.io.IOException"
        //   {@3484} "directory" -> {java.lang.reflect.Method@3403} "public boolean java.io.File.isDirectory()"
        //   {@3485} "file" -> {java.lang.reflect.Method@3419} "public boolean java.io.File.isFile()"
        //   {@3486} "freeSpace" -> {java.lang.reflect.Method@3428} "public long java.io.File.getFreeSpace()"
        //   {@3487} "hidden" -> {java.lang.reflect.Method@3385} "public boolean java.io.File.isHidden()"
        //   {@3488} "name" -> {java.lang.reflect.Method@3380} "public java.lang.String java.io.File.getName()"
        //   {@3489} "parent" -> {java.lang.reflect.Method@3391} "public java.lang.String java.io.File.getParent()"
        //   {@3490} "parentFile" -> {java.lang.reflect.Method@3407} "public java.io.File java.io.File.getParentFile()"
        //   {@3491} "path" -> {java.lang.reflect.Method@3395} "public java.lang.String java.io.File.getPath()"
        //   {@3492} "totalSpace" -> {java.lang.reflect.Method@3427} "public long java.io.File.getTotalSpace()"
        //   {@3493} "usableSpace" -> {java.lang.reflect.Method@3429} "public long java.io.File.getUsableSpace()"
        //
        // Mapping of File attribute fails, because File contains absoluteFile attribute and
        // com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.inProgress is checked and OpenDataException is thrown

        mbs.registerMBean(new SingleFileAttribute3(), name);
    }

}
