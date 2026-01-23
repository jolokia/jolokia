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
package org.jolokia.converter.object;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class OpenTypeHelperTest {

    @Test
    public void jsonRepresentationOfSimpleTypes() {
        // both primitive and wrapped types refer to proper open type, but primitive ones are
        // NOT described by "Open" version of the attribute/parameter/operation info.
        // Also, if a bean is not an MXBean, there are no openTypes in the descriptor of MBeanAttributeInfo
        // finally - openTypes for primitive types still use wrapped class/typeNames
        //
        // 6 = {javax.management.openmbean.OpenMBeanAttributeInfoSupport@4757} "javax.management.openmbean.OpenMBeanAttributeInfoSupport(name=Integer,openType=javax.management.openmbean.SimpleType(name=java.lang.Integer),default=null,minValue=null,maxValue=null,legalValues=null,descriptor={openType=javax.management.openmbean.SimpleType(name=java.lang.Integer), originalType=java.lang.Integer})"
        //  attributeType: java.lang.String  = {@4899} "java.lang.Integer"
        //  ...
        //  description: java.lang.String  = {@4900} "Integer"
        //  descriptor: javax.management.Descriptor  = {javax.management.ImmutableDescriptor@4901} "{openType=javax.management.openmbean.SimpleType(name=java.lang.Integer), originalType=java.lang.Integer}"
        //   hashCode: int  = -1
        //   names: java.lang.String[]  = {java.lang.String[2]@4903} ["openType", "originalType"]
        //    0 = {@4893} "openType"
        //    1 = {@4894} "originalType"
        //   values: java.lang.Object[]  = {java.lang.Object[2]@4904}
        //    0 = {javax.management.openmbean.SimpleType@4897} "javax.management.openmbean.SimpleType(name=java.lang.Integer)"
        //    1 = {@4899} "java.lang.Integer"
        //  is: boolean  = false
        //  isRead: boolean  = true
        //  isWrite: boolean  = false
        //  ...
        //  name: java.lang.String  = {@4900} "Integer"
        //  openType: javax.management.openmbean.OpenType  = {javax.management.openmbean.SimpleType@4897} "javax.management.openmbean.SimpleType(name=java.lang.Integer)"
        //
        // 17 = {javax.management.MBeanAttributeInfo@4768} "javax.management.MBeanAttributeInfo[description=PrimitiveInteger, name=PrimitiveInteger, type=int, read-only, descriptor={openType=javax.management.openmbean.SimpleType(name=java.lang.Integer), originalType=int}]"
        //  attributeType: java.lang.String  = {@4906} "int"
        //  description: java.lang.String  = {@4907} "PrimitiveInteger"
        //  descriptor: javax.management.Descriptor  = {javax.management.ImmutableDescriptor@4908} "{openType=javax.management.openmbean.SimpleType(name=java.lang.Integer), originalType=int}"
        //   hashCode: int  = -1
        //   names: java.lang.String[]  = {java.lang.String[2]@4910} ["openType", "originalType"]
        //    0 = {@4893} "openType"
        //    1 = {@4894} "originalType"
        //   values: java.lang.Object[]  = {java.lang.Object[2]@4911}
        //    0 = {javax.management.openmbean.SimpleType@4897} "javax.management.openmbean.SimpleType(name=java.lang.Integer)"
        //    1 = {@4906} "int"
        //  is: boolean  = false
        //  isRead: boolean  = true
        //  isWrite: boolean  = false
        //  name: java.lang.String  = {@4907} "PrimitiveInteger"

        assertEquals(OpenTypeHelper.toJSON(SimpleType.BOOLEAN, null), SimpleType.BOOLEAN.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.CHARACTER, null), SimpleType.CHARACTER.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.BYTE, null), SimpleType.BYTE.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.SHORT, null), SimpleType.SHORT.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.INTEGER, null), SimpleType.INTEGER.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.LONG, null), SimpleType.LONG.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.FLOAT, null), SimpleType.FLOAT.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.DOUBLE, null), SimpleType.DOUBLE.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.STRING, null), SimpleType.STRING.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.OBJECTNAME, null), SimpleType.OBJECTNAME.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.VOID, null), SimpleType.VOID.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.DATE, null), SimpleType.DATE.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.BIGDECIMAL, null), SimpleType.BIGDECIMAL.getTypeName());
        assertEquals(OpenTypeHelper.toJSON(SimpleType.BIGINTEGER, null), SimpleType.BIGINTEGER.getTypeName());
    }

    @Test
    public void jsonRepresentationOfArrayTypes() throws Exception {
        ArrayType<int[]> primitive1DIntegerArray = ArrayType.getPrimitiveArrayType(int[].class);
        JSONObject primitive1DIntegerArrayJSON = OpenTypeHelper.toJSON(primitive1DIntegerArray, null);
        assertEquals(primitive1DIntegerArrayJSON.get("kind"), "array");
        assertEquals(primitive1DIntegerArrayJSON.get("class"), "[I");
        assertEquals(primitive1DIntegerArrayJSON.get("type"), "[I");
        assertEquals(primitive1DIntegerArrayJSON.get("primitive"), true);
        assertEquals(primitive1DIntegerArrayJSON.get("dimension"), 1);
        assertEquals(primitive1DIntegerArrayJSON.get("elemType"), OpenTypeHelper.toJSON(SimpleType.INTEGER, null));

        ArrayType<int[][]> primitive2DIntegerArray = ArrayType.getPrimitiveArrayType(int[][].class);
        JSONObject primitive2DIntegerArrayJSON = OpenTypeHelper.toJSON(primitive2DIntegerArray, null);
        assertEquals(primitive2DIntegerArrayJSON.get("kind"), "array");
        assertEquals(primitive2DIntegerArrayJSON.get("class"), "[[I");
        assertEquals(primitive2DIntegerArrayJSON.get("type"), "[[I");
        assertEquals(primitive2DIntegerArrayJSON.get("primitive"), true);
        assertEquals(primitive2DIntegerArrayJSON.get("dimension"), 2);
        assertEquals(primitive2DIntegerArrayJSON.get("elemType"), OpenTypeHelper.toJSON(SimpleType.INTEGER, null));

        ArrayType<int[]> wrapper1DIntegerArray = new ArrayType<>(1, SimpleType.INTEGER);
        JSONObject wrapper1DIntegerArrayJSON = OpenTypeHelper.toJSON(wrapper1DIntegerArray, null);
        assertEquals(wrapper1DIntegerArrayJSON.get("kind"), "array");
        assertEquals(wrapper1DIntegerArrayJSON.get("class"), "[Ljava.lang.Integer;");
        assertEquals(wrapper1DIntegerArrayJSON.get("type"), "[Ljava.lang.Integer;");
        assertEquals(wrapper1DIntegerArrayJSON.get("primitive"), false);
        assertEquals(wrapper1DIntegerArrayJSON.get("dimension"), 1);
        assertEquals(wrapper1DIntegerArrayJSON.get("elemType"), OpenTypeHelper.toJSON(SimpleType.INTEGER, null));

        ArrayType<Integer[][]> wrapper2DIntegerArray = new ArrayType<>(2, SimpleType.INTEGER);
        JSONObject wrapper2DIntegerArrayJSON = OpenTypeHelper.toJSON(wrapper2DIntegerArray, null);
        assertEquals(wrapper2DIntegerArrayJSON.get("kind"), "array");
        assertEquals(wrapper2DIntegerArrayJSON.get("class"), "[[Ljava.lang.Integer;");
        assertEquals(wrapper2DIntegerArrayJSON.get("type"), "[[Ljava.lang.Integer;");
        assertEquals(wrapper2DIntegerArrayJSON.get("primitive"), false);
        assertEquals(wrapper2DIntegerArrayJSON.get("dimension"), 2);
        assertEquals(wrapper2DIntegerArrayJSON.get("elemType"), OpenTypeHelper.toJSON(SimpleType.INTEGER, null));

        MBeanInfo info = ManagementFactory.getPlatformMBeanServer().getMBeanInfo(new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME));
        Optional<MBeanAttributeInfo> v = Arrays.stream(info.getAttributes()).filter(i -> "InputArguments".equals(i.getName())).findFirst();
        MBeanAttributeInfo attributeInfo = v.orElseThrow(RuntimeException::new);
        if (attributeInfo instanceof OpenMBeanAttributeInfo openMBeanAttributeInfo) {
            JSONObject json = (JSONObject) OpenTypeHelper.toJSON(openMBeanAttributeInfo.getOpenType(), null);
            assertEquals(json.get("kind"), OpenTypeHelper.Kind.array.name());
            assertEquals(json.get("class"), "[Ljava.lang.String;");
            assertEquals(json.get("type"), "[Ljava.lang.String;");
            assertEquals(json.get("primitive"), false);
            assertEquals(json.get("dimension"), 1);
            assertEquals(json.get("elemType"), OpenTypeHelper.toJSON(SimpleType.STRING, null));
        } else {
            fail("Expected OpenMBeanAttributeInfo for \"InputArguments\"");
        }
    }

    @Test
    public void jsonRepresentationOfCompositeTypes() throws Exception {
        MBeanInfo info = ManagementFactory.getPlatformMBeanServer().getMBeanInfo(new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME));
        Optional<MBeanAttributeInfo> v = Arrays.stream(info.getAttributes()).filter(i -> "HeapMemoryUsage".equals(i.getName())).findFirst();
        MBeanAttributeInfo attributeInfo = v.orElseThrow(RuntimeException::new);
        if (attributeInfo instanceof OpenMBeanAttributeInfo openMBeanAttributeInfo) {
            JSONObject json = (JSONObject) OpenTypeHelper.toJSON(openMBeanAttributeInfo.getOpenType(), null);
            assertEquals(json.get("kind"), OpenTypeHelper.Kind.composite.name());
            // here's an example of the difference between a class and a type for composite type
            assertEquals(json.get("class"), "javax.management.openmbean.CompositeData");
            assertEquals(json.get("type"), "java.lang.management.MemoryUsage");
            Object items = json.get("items");
            if (items instanceof JSONObject itemsMap) {
                assertEquals(itemsMap.get("used"), OpenTypeHelper.toJSON(SimpleType.LONG, null));
            } else {
                fail("Expected a map of items");
            }
        } else {
            fail("Expected OpenMBeanAttributeInfo for \"HeapMemoryUsage\"");
        }
    }

    @Test
    public void jsonRepresentationOfCompositeArrayType() throws Exception {
        MBeanServer jmx = ManagementFactory.getPlatformMBeanServer();
        ObjectName jfrName = new ObjectName("jdk.management.jfr:type=FlightRecorder");
        if (!jmx.isRegistered(jfrName)) {
            throw new SkipException("No JFR MBean found");
        }

        MBeanInfo info = jmx.getMBeanInfo(jfrName);
        Optional<MBeanAttributeInfo> v = Arrays.stream(info.getAttributes()).filter(i -> "EventTypes".equals(i.getName())).findFirst();
        MBeanAttributeInfo attributeInfo = v.orElseThrow(RuntimeException::new);
        if (attributeInfo instanceof OpenMBeanAttributeInfo openMBeanAttributeInfo) {
            JSONObject json = (JSONObject) OpenTypeHelper.toJSON(openMBeanAttributeInfo.getOpenType(), attributeInfo);
            assertEquals(json.get("kind"), OpenTypeHelper.Kind.array.name());
            assertEquals(json.get("class"), "[Ljavax.management.openmbean.CompositeData;");
//            assertEquals(json.get("type"), "[Ljavax.management.openmbean.CompositeData;");
//            assertEquals(json.get("type"), "[Ljdk.management.jfr.EventTypeInfo;");
            // Jolokia special, because normally we get an array of composite data
            assertEquals(json.get("type"), "java.util.List<jdk.management.jfr.EventTypeInfo>");
            assertEquals(json.get("primitive"), false);
            assertEquals(json.get("dimension"), 1);

            Object elemType = json.get("elemType");
            if (elemType instanceof JSONObject elem) {
                assertEquals(elem.get("kind"), OpenTypeHelper.Kind.composite.name());
                assertEquals(elem.get("class"), "javax.management.openmbean.CompositeData");
                assertEquals(elem.get("type"), "jdk.management.jfr.EventTypeInfo");
                Object items = elem.get("items");
                if (items instanceof JSONObject itemsMap) {
                    assertEquals(itemsMap.get("categoryNames"), OpenTypeHelper.toJSON(new ArrayType<>(1, SimpleType.STRING), null));
                    Object settingDescriptors = itemsMap.get("settingDescriptors");
                    if (settingDescriptors instanceof JSONObject settingDescriptorsJSON) {
                        assertEquals(settingDescriptorsJSON.get("kind"), OpenTypeHelper.Kind.array.name());
                        assertEquals(settingDescriptorsJSON.get("class"), "[Ljavax.management.openmbean.CompositeData;");
                        // we don't have "java.util.List<jdk.management.jfr.SettingDescriptorInfo>" here, because
                        // MBeanFeatureInfo where this information is stored is only available at the top level
                        assertEquals(settingDescriptorsJSON.get("type"), "[Ljdk.management.jfr.SettingDescriptorInfo;");
                        Object elem2type = settingDescriptorsJSON.get("elemType");
                        if (elem2type instanceof JSONObject elem2) {
                            assertEquals(elem2.get("kind"), OpenTypeHelper.Kind.composite.name());
                            assertEquals(elem2.get("class"), "javax.management.openmbean.CompositeData");
                            assertEquals(elem2.get("type"), "jdk.management.jfr.SettingDescriptorInfo");
                            Object items2 = elem2.get("items");
                            if (items2 instanceof JSONObject items2Map) {
                                assertEquals(items2Map.get("contentType"), OpenTypeHelper.toJSON(SimpleType.STRING, null));
                            } else {
                                fail("Expected a map of items");
                            }
                        } else {
                            fail("Expected a map of items");
                        }
                    } else {
                        fail("Expected a map of items");
                    }
                } else {
                    fail("Expected a map of items");
                }
            } else {
                fail("Expected a map for CompositeData");
            }
        } else {
            fail("Expected OpenMBeanAttributeInfo for \"EventTypes\"");
        }
    }

    @Test
    public void jsonRepresentationOfTabularTypes() throws Exception {
        MBeanServer jmx = ManagementFactory.getPlatformMBeanServer();
        ObjectName jfrName = new ObjectName("jdk.management.jfr:type=FlightRecorder");
        if (!jmx.isRegistered(jfrName)) {
            throw new SkipException("No JFR MBean found");
        }

        MBeanInfo info = jmx.getMBeanInfo(jfrName);
        Optional<MBeanOperationInfo> v = Arrays.stream(info.getOperations()).filter(i -> "getRecordingOptions".equals(i.getName())).findFirst();
        MBeanOperationInfo operationInfo = v.orElseThrow(RuntimeException::new);
        // strange, but true
        assertFalse(operationInfo instanceof OpenMBeanOperationInfo);
        // only com.sun.management.HotSpotDiagnosticMXBean.getVMOption() (and 2 methods from Logging MBean) provide
        // OpenMBeanOperationInfo and we could access the OpenType for return value using:
        //     ((OpenMBeanOperationInfo) operationInfo).getReturnOpenType();
        // but this should work too
        JSONObject json = (JSONObject) OpenTypeHelper.toJSON((OpenType<?>) operationInfo.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), operationInfo);
        assertEquals(json.get("kind"), OpenTypeHelper.Kind.tabular.name());
        assertEquals(json.get("index"), new JSONArray(List.of("key")));
        assertEquals(json.get("rowType"), OpenTypeHelper.toJSON(((TabularType) operationInfo.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD)).getRowType(), null));
    }

}
