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

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanInfo;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;

import org.jolokia.client.jmxadapter.beans.Example1;
import org.jolokia.client.jmxadapter.beans.Example1MXBean;
import org.jolokia.client.jmxadapter.beans.Example2;
import org.jolokia.client.jmxadapter.beans.Example2MBean;
import org.jolokia.client.jmxadapter.beans.User;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TypeHelperTest {

    @Test
    public void uniqueTypeCaching() throws OpenDataException {
        // checking types which do not use CompositeType / TabularType

        assertSame(TypeHelper.cache("t1a", "Z", null).type(), Boolean.TYPE);
        assertSame(TypeHelper.cache("t1b", "boolean", null).type(), Boolean.TYPE);
        assertSame(TypeHelper.cache("t1b", "boolean", null).type(), boolean.class);
        assertSame(TypeHelper.cache("t1c", "java.lang.Boolean", null).type(), Boolean.class);
        assertSame(TypeHelper.cache("t1c", "java.lang.Boolean", null).openType(), SimpleType.BOOLEAN);

        assertSame(TypeHelper.cache("t2a", "I", null).type(), Integer.TYPE);
        assertSame(TypeHelper.cache("t2a", "I", null).openType(), SimpleType.INTEGER);
        assertSame(TypeHelper.cache("t2b", "int", null).type(), Integer.TYPE);
        assertSame(TypeHelper.cache("t2b", "int", null).type(), int.class);
        assertNull(TypeHelper.cache("t2c", "integer", null).type());
        assertSame(TypeHelper.cache("t2d", "java.lang.Integer", null).type(), Integer.class);

        assertSame(TypeHelper.cache("t3a", "[I", null).type(), int[].class);
        assertSame(TypeHelper.cache("t3b", "[[I", null).type(), int[][].class);
        assertSame(TypeHelper.cache("t3c", "[[[[I", null).type(), int[][][][].class);
        assertEquals(TypeHelper.cache("t3d", "[[[I", null).openType(), ArrayType.getPrimitiveArrayType(int[][][].class));

        assertEquals(TypeHelper.cache("t4a", "[Ljava.lang.Integer;", null).openType(), new ArrayType<>(1, SimpleType.INTEGER));
        assertEquals(TypeHelper.cache("t4b", "[[[[Ljavax.management.ObjectName;", null).openType(), new ArrayType<>(4, SimpleType.OBJECTNAME));
        assertEquals(TypeHelper.cache("t4c", "[[[[Ljava.lang.String;", null).openType(), new ArrayType<>(2, new ArrayType<>(2, SimpleType.STRING)));
        // yes...
        assertEquals(new ArrayType<>(7, SimpleType.STRING), new ArrayType<>(3, new ArrayType<>(2, new ArrayType<>(2, SimpleType.STRING))));

        assertNull(TypeHelper.cache("t5a", "java.lang.Object", null).openType());
        assertNull(TypeHelper.cache("t5b", "[Ljava.lang.Object;", null).openType());
        assertNotNull(TypeHelper.cache("t5c", "[Ljava.lang.Object;", null).type());

        assertEquals(TypeHelper.cache("t6a", "java.util.Date", null).openType(), SimpleType.DATE);
        assertEquals(TypeHelper.cache("t6b", "[Ljava.util.Date;", null).openType(), new ArrayType<>(1, SimpleType.DATE));
        assertEquals(TypeHelper.cache("t6b", "[Ljava.util.Date;", null).type(), Date[].class);

        // with typo
        assertNull(TypeHelper.cache("t7a", "java.util.Data", null).openType());
        assertNull(TypeHelper.cache("t7b", "[Ljava.util.Data;", null).openType());
        assertNull(TypeHelper.cache("t7c", "[Ljava.util.Data;", null).type());
        assertEquals(TypeHelper.cache("t7d", "[Ljava.util.Data;", null).typeName(), "[Ljava.util.Data;");
    }

    @Test
    public void ambiguousTypeCaching() {
        // checking types which can't be dissected without particular data objects

        CachedType t = TypeHelper.cache("t1", CompositeData.class.getName(), null);
        assertEquals(t.type(), CompositeData.class);
        assertEquals(t.typeName(), CompositeData.class.getName());
        assertNull(t.openType());

        t = TypeHelper.cache("t2", CompositeData[].class.getName(), null);
        assertEquals(t.type(), CompositeData[].class);
        assertEquals(t.typeName(), CompositeData[].class.getName());
        assertNull(t.openType());

        t = TypeHelper.cache("t3", "[[[[[Ljavax.management.openmbean.CompositeData;", null);
        assertEquals(t.type(), CompositeData[][][][][].class);
        assertEquals(t.typeName(), CompositeData[][][][][].class.getName());
        assertNull(t.openType());

        t = TypeHelper.cache("t4", TabularData.class.getName(), null);
        assertEquals(t.type(), TabularData.class);
        assertEquals(t.typeName(), TabularData.class.getName());
        assertNull(t.openType());

        t = TypeHelper.cache("t5", TabularData[].class.getName(), null);
        assertEquals(t.type(), TabularData[].class);
        assertEquals(t.typeName(), TabularData[].class.getName());
        assertNull(t.openType());

        t = TypeHelper.cache("t6", "[[[[[Ljavax.management.openmbean.TabularData;", null);
        assertEquals(t.type(), TabularData[][][][][].class);
        assertEquals(t.typeName(), TabularData[][][][][].class.getName());
        assertNull(t.openType());
    }

    @Test
    public void typeCachingFromMBeanInfo() throws Exception {
        MBeanServer jmx = ManagementFactory.getPlatformMBeanServer();

        ObjectName example1a = new ObjectName("jolokia:test=TypeHelperTest,name=example1a");
        ObjectName example1b = new ObjectName("jolokia:test=TypeHelperTest,name=example1b");
        ObjectName example2a = new ObjectName("jolokia:test=TypeHelperTest,name=example2a");
        ObjectName example2b = new ObjectName("jolokia:test=TypeHelperTest,name=example2b");

        // 1 wrapped in com.sun.jmx.mbeanserver.MXBeanSupport
        jmx.registerMBean(new Example1(), example1a);
        // 3 wrapped in com.sun.jmx.mbeanserver.StandardMBeanSupport
        jmx.registerMBean(new StandardMBean(new Example1(), Example1MXBean.class), example1b);
        jmx.registerMBean(new Example2(), example2a);
        jmx.registerMBean(new StandardMBean(new Example2(), Example2MBean.class), example2b);

        MBeanInfo info1a = jmx.getMBeanInfo(example1a);
        assertFalse(info1a instanceof OpenMBeanInfo, "Unexpected, but true");
        MBeanInfo info1b = jmx.getMBeanInfo(example1b);
        assertFalse(info1b instanceof OpenMBeanInfo);
        MBeanInfo info2a = jmx.getMBeanInfo(example2a);
        assertFalse(info2a instanceof OpenMBeanInfo);
        MBeanInfo info2b = jmx.getMBeanInfo(example2b);
        assertFalse(info2b instanceof OpenMBeanInfo);

        // 1a

        MBeanAttributeInfo i1aShort = attrInfo(info1a, "Short");
        assertEquals(TypeHelper.cache("i1aShort", i1aShort.getType(), null).type(), Short.class);
        assertEquals(TypeHelper.cache("i1aShort", i1aShort.getType(), null).typeName(), "java.lang.Short");
        assertEquals(TypeHelper.cache("i1aShort", i1aShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i1aPrimitiveShort = attrInfo(info1a, "PrimitiveShort");
        assertEquals(TypeHelper.cache("i1aPrimitiveShort", i1aPrimitiveShort.getType(), null).type(), Short.TYPE);
        assertEquals(TypeHelper.cache("i1aPrimitiveShort", i1aPrimitiveShort.getType(), null).typeName(), "short");
        assertEquals(TypeHelper.cache("i1aPrimitiveShort", i1aPrimitiveShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i1aPrimitiveShortArray = attrInfo(info1a, "PrimitiveShortArray");
        assertEquals(TypeHelper.cache("i1aPrimitiveShortArray", i1aPrimitiveShortArray.getType(), null).type(), short[].class);
        assertEquals(TypeHelper.cache("i1aPrimitiveShortArray", i1aPrimitiveShortArray.getType(), null).typeName(), "[S");
        assertEquals(TypeHelper.cache("i1aPrimitiveShortArray", i1aPrimitiveShortArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[].class));
        MBeanAttributeInfo i1aShortArray = attrInfo(info1a, "ShortArray");
        assertEquals(TypeHelper.cache("i1aShortArray", i1aShortArray.getType(), null).type(), Short[].class);
        assertEquals(TypeHelper.cache("i1aShortArray", i1aShortArray.getType(), null).typeName(), "[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i1aShortArray", i1aShortArray.getType(), null).openType(), new ArrayType<>(1, SimpleType.SHORT));
        MBeanAttributeInfo i1aPrimitiveShort2DArray = attrInfo(info1a, "PrimitiveShort2DArray");
        assertEquals(TypeHelper.cache("i1aPrimitiveShort2DArray", i1aPrimitiveShort2DArray.getType(), null).type(), short[][].class);
        assertEquals(TypeHelper.cache("i1aPrimitiveShort2DArray", i1aPrimitiveShort2DArray.getType(), null).typeName(), "[[S");
        assertEquals(TypeHelper.cache("i1aPrimitiveShort2DArray", i1aPrimitiveShort2DArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[][].class));
        MBeanAttributeInfo i1aShort2DArray = attrInfo(info1a, "Short2DArray");
        assertEquals(TypeHelper.cache("i1aShort2DArray", i1aShort2DArray.getType(), null).type(), Short[][].class);
        assertEquals(TypeHelper.cache("i1aShort2DArray", i1aShort2DArray.getType(), null).typeName(), "[[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i1aShort2DArray", i1aShort2DArray.getType(), null).openType(), new ArrayType<>(2, SimpleType.SHORT));

        MBeanAttributeInfo i1aProperTabularType = attrInfo(info1a, "ProperTabularType");
        assertEquals(TypeHelper.cache("i1aProperTabularType", i1aProperTabularType.getType(), null).type(), TabularData.class);
        assertEquals(TypeHelper.cache("i1aProperTabularType", i1aProperTabularType.getType(), null).typeName(), TabularData.class.getName());
        assertEquals(i1aProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), Example1.typeOfProperTabularTypeAttribute());
        assertNull(TypeHelper.cache("i1aProperTabularType", i1aProperTabularType.getType(), null).openType());
        MBeanAttributeInfo i1aProperTabularTypeArray = attrInfo(info1a, "ProperTabularTypeArray");
        assertEquals(TypeHelper.cache("i1aProperTabularTypeArray", i1aProperTabularTypeArray.getType(), null).type(), TabularData[].class);
        assertEquals(TypeHelper.cache("i1aProperTabularTypeArray", i1aProperTabularTypeArray.getType(), null).typeName(), "[Ljavax.management.openmbean.TabularData;");
        assertEquals(i1aProperTabularTypeArray.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), new ArrayType<>(1, Example1.typeOfProperTabularTypeAttribute()));
        assertNull(TypeHelper.cache("i1aProperTabularTypeArray", i1aProperTabularTypeArray.getType(), null).openType());
        MBeanAttributeInfo i1aList = attrInfo(info1a, "List");
        assertEquals(TypeHelper.cache("i1aList", i1aList.getType(), null).type(), String[].class);
        assertEquals(TypeHelper.cache("i1aList", i1aList.getType(), null).typeName(), "[Ljava.lang.String;");
        assertEquals(TypeHelper.cache("i1aList", i1aList.getType(), null).openType(), new ArrayType<>(1, SimpleType.STRING));
        MBeanAttributeInfo i1aLists = attrInfo(info1a, "Lists");
        assertEquals(TypeHelper.cache("i1aList", i1aLists.getType(), null).type(), String[][].class);
        assertEquals(TypeHelper.cache("i1aList", i1aLists.getType(), null).typeName(), "[[Ljava.lang.String;");
        assertEquals(TypeHelper.cache("i1aList", i1aLists.getType(), null).openType(), new ArrayType<>(2, SimpleType.STRING));
        MBeanAttributeInfo i1aSet = attrInfo(info1a, "Set");
        assertEquals(TypeHelper.cache("i1aSet", i1aSet.getType(), null).type(), Short[].class);
        assertEquals(TypeHelper.cache("i1aSet", i1aSet.getType(), null).typeName(), "[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i1aSet", i1aSet.getType(), null).openType(), new ArrayType<>(1, SimpleType.SHORT));
        MBeanAttributeInfo i1aSets = attrInfo(info1a, "Sets");
        assertEquals(TypeHelper.cache("i1aSets", i1aSets.getType(), null).type(), Short[][].class);
        assertEquals(TypeHelper.cache("i1aSets", i1aSets.getType(), null).typeName(), "[[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i1aSets", i1aSets.getType(), null).openType(), new ArrayType<>(2, SimpleType.SHORT));

        // there's only one getter in javax.management.openmbean.CompositeData "bean" - "CompositeType"
        MBeanAttributeInfo i1aCompositeData = attrInfo(info1a, "CompositeData");
        assertEquals(TypeHelper.cache("i1aCompositeData", i1aCompositeData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aCompositeData", i1aCompositeData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i1aCompositeData", i1aCompositeData.getType(), null).openType());
        CompositeType i1aCompositeType = (CompositeType) ((OpenMBeanAttributeInfo) i1aCompositeData).getOpenType();
        assertEquals(TypeHelper.cache("i1aCompositeData2", i1aCompositeData.getType(), i1aCompositeType).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aCompositeData2", i1aCompositeData.getType(), i1aCompositeType).typeName(), CompositeData.class.getName());
        assertEquals(TypeHelper.cache("i1aCompositeData2", i1aCompositeData.getType(), i1aCompositeType).openType(), i1aCompositeType);
        assertEquals(i1aCompositeType.keySet().size(), 1);
        assertTrue(i1aCompositeType.containsKey("compositeType"));
        MBeanAttributeInfo i1aCompositeDataArray = attrInfo(info1a, "CompositeDataArray");
        assertEquals(TypeHelper.cache("i1aCompositeDataArray", i1aCompositeDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aCompositeDataArray", i1aCompositeDataArray.getType(), null).typeName(), "[Ljavax.management.openmbean.CompositeData;");
        assertNull(TypeHelper.cache("i1aCompositeDataArray", i1aCompositeDataArray.getType(), null).openType());
        @SuppressWarnings("unchecked")
        ArrayType<CompositeData> i1aArrayType = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aCompositeDataArray).getOpenType();
        assertEquals(TypeHelper.cache("i1aCompositeDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aCompositeDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).typeName(), CompositeData[].class.getName());
        assertEquals(TypeHelper.cache("i1aCompositeDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).openType(), i1aArrayType);
        i1aCompositeType = (CompositeType) i1aArrayType.getElementOpenType();
        assertEquals(i1aCompositeType.keySet().size(), 1);
        assertTrue(i1aCompositeType.containsKey("compositeType"));

        // TabularData attribute is also treated as "bean" and introspected into CompositeData
        MBeanAttributeInfo i1aTabularData = attrInfo(info1a, "TabularData");
        assertEquals(TypeHelper.cache("i1aTabularData", i1aTabularData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aTabularData", i1aTabularData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i1aTabularData", i1aTabularData.getType(), null).openType());
        i1aCompositeType = (CompositeType) ((OpenMBeanAttributeInfo) i1aTabularData).getOpenType();
        assertEquals(TypeHelper.cache("i1aTabularData2", i1aTabularData.getType(), i1aCompositeType).type(), CompositeData.class);
        // typeName comes from OpenType, so it's actually TabularData, not CompositeData
        assertEquals(TypeHelper.cache("i1aTabularData2", i1aTabularData.getType(), i1aCompositeType).typeName(), TabularData.class.getName());
        assertEquals(TypeHelper.cache("i1aTabularData2", i1aTabularData.getType(), i1aCompositeType).openType(), i1aCompositeType);
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("tabularType"));
        assertTrue(i1aCompositeType.containsKey("empty"));
        MBeanAttributeInfo i1aTabularDataArray = attrInfo(info1a, "TabularDataArray");
        assertEquals(TypeHelper.cache("i1aTabularDataArray", i1aTabularDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aTabularDataArray", i1aTabularDataArray.getType(), null).typeName(), "[Ljavax.management.openmbean.CompositeData;");
        assertNull(TypeHelper.cache("i1aTabularDataArray", i1aTabularDataArray.getType(), null).openType());
        @SuppressWarnings("unchecked")
        ArrayType<CompositeData> i1aArrayType2 = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aTabularDataArray).getOpenType();
        assertEquals(TypeHelper.cache("i1aTabularDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).type(), CompositeData[].class);
        // we do some Jolokia special conversion in org.jolokia.converter.object.OpenTypeHelper.toJSON(javax.management.openmbean.ArrayType<?>, javax.management.MBeanFeatureInfo) too
        assertEquals(TypeHelper.cache("i1aTabularDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).typeName(), /*CompositeData[].class.getName()*/TabularData[].class.getName());
        assertEquals(TypeHelper.cache("i1aTabularDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).openType(), i1aArrayType2);
        i1aCompositeType = (CompositeType) i1aArrayType2.getElementOpenType();
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("tabularType"));
        assertTrue(i1aCompositeType.containsKey("empty"));

        // "bean" is converted to CompositeData
        MBeanAttributeInfo i1aUser = attrInfo(info1a, "User");
        assertEquals(TypeHelper.cache("i1aUser", i1aUser.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aUser", i1aUser.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i1aUser", i1aUser.getType(), null).openType());
        i1aCompositeType = (CompositeType) ((OpenMBeanAttributeInfo) i1aUser).getOpenType();
        assertEquals(TypeHelper.cache("i1aUser2", i1aUser.getType(), i1aCompositeType).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aUser2", i1aUser.getType(), i1aCompositeType).typeName(), User.class.getName());
        assertEquals(TypeHelper.cache("i1aUser2", i1aUser.getType(), i1aCompositeType).openType(), i1aCompositeType);
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("name"));
        assertTrue(i1aCompositeType.containsKey("address"));
        i1aCompositeType = (CompositeType) i1aCompositeType.getType("address");
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("city"));
        assertEquals(i1aCompositeType.getType("city"), SimpleType.STRING);
        assertTrue(i1aCompositeType.containsKey("zip"));
        assertEquals(i1aCompositeType.getType("zip"), SimpleType.LONG);

        MBeanAttributeInfo i1aUsers = attrInfo(info1a, "Users");
        assertEquals(TypeHelper.cache("i1aUsers", i1aUsers.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aUsers", i1aUsers.getType(), null).typeName(), CompositeData[].class.getName());
        assertNull(TypeHelper.cache("i1aUsers", i1aUsers.getType(), null).openType());
        @SuppressWarnings("unchecked")
        ArrayType<CompositeData> i1aArrayType3 = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aUsers).getOpenType();
        assertEquals(TypeHelper.cache("i1aUsers2", i1aUsers.getType(), i1aArrayType3).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aUsers2", i1aUsers.getType(), i1aArrayType3).typeName(), User[].class.getName());
        assertEquals(TypeHelper.cache("i1aUsers2", i1aUsers.getType(), i1aArrayType3).openType(), i1aArrayType3);
        i1aCompositeType = (CompositeType) i1aArrayType3.getElementOpenType();
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("name"));
        assertTrue(i1aCompositeType.containsKey("address"));
        i1aCompositeType = (CompositeType) i1aCompositeType.getType("address");
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("city"));
        assertEquals(i1aCompositeType.getType("city"), SimpleType.STRING);
        assertTrue(i1aCompositeType.containsKey("zip"));
        assertEquals(i1aCompositeType.getType("zip"), SimpleType.LONG);

        // 1b

        MBeanAttributeInfo i1bShort = attrInfo(info1b, "Short");
        assertEquals(TypeHelper.cache("i1bShort", i1bShort.getType(), null).type(), Short.class);
        assertEquals(TypeHelper.cache("i1bShort", i1bShort.getType(), null).typeName(), "java.lang.Short");
        assertEquals(TypeHelper.cache("i1bShort", i1bShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i1bPrimitiveShort = attrInfo(info1b, "PrimitiveShort");
        assertEquals(TypeHelper.cache("i1bPrimitiveShort", i1bPrimitiveShort.getType(), null).type(), Short.TYPE);
        assertEquals(TypeHelper.cache("i1bPrimitiveShort", i1bPrimitiveShort.getType(), null).typeName(), "short");
        assertEquals(TypeHelper.cache("i1bPrimitiveShort", i1bPrimitiveShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i1bPrimitiveShortArray = attrInfo(info1b, "PrimitiveShortArray");
        assertEquals(TypeHelper.cache("i1bPrimitiveShortArray", i1bPrimitiveShortArray.getType(), null).type(), short[].class);
        assertEquals(TypeHelper.cache("i1bPrimitiveShortArray", i1bPrimitiveShortArray.getType(), null).typeName(), "[S");
        assertEquals(TypeHelper.cache("i1bPrimitiveShortArray", i1bPrimitiveShortArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[].class));
        MBeanAttributeInfo i1bShortArray = attrInfo(info1b, "ShortArray");
        assertEquals(TypeHelper.cache("i1bShortArray", i1bShortArray.getType(), null).type(), Short[].class);
        assertEquals(TypeHelper.cache("i1bShortArray", i1bShortArray.getType(), null).typeName(), "[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i1bShortArray", i1bShortArray.getType(), null).openType(), new ArrayType<>(1, SimpleType.SHORT));
        MBeanAttributeInfo i1bPrimitiveShort2DArray = attrInfo(info1b, "PrimitiveShort2DArray");
        assertEquals(TypeHelper.cache("i1bPrimitiveShort2DArray", i1bPrimitiveShort2DArray.getType(), null).type(), short[][].class);
        assertEquals(TypeHelper.cache("i1bPrimitiveShort2DArray", i1bPrimitiveShort2DArray.getType(), null).typeName(), "[[S");
        assertEquals(TypeHelper.cache("i1bPrimitiveShort2DArray", i1bPrimitiveShort2DArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[][].class));
        MBeanAttributeInfo i1bShort2DArray = attrInfo(info1b, "Short2DArray");
        assertEquals(TypeHelper.cache("i1bShort2DArray", i1bShort2DArray.getType(), null).type(), Short[][].class);
        assertEquals(TypeHelper.cache("i1bShort2DArray", i1bShort2DArray.getType(), null).typeName(), "[[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i1bShort2DArray", i1bShort2DArray.getType(), null).openType(), new ArrayType<>(2, SimpleType.SHORT));

        MBeanAttributeInfo i1bProperTabularType = attrInfo(info1b, "ProperTabularType");
        assertEquals(TypeHelper.cache("i1bProperTabularType", i1bProperTabularType.getType(), null).type(), Map.class);
        assertEquals(TypeHelper.cache("i1bProperTabularType", i1bProperTabularType.getType(), null).typeName(), Map.class.getName());
        assertNull(i1bProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
        assertNull(TypeHelper.cache("i1bProperTabularType", i1bProperTabularType.getType(), null).openType());
        MBeanAttributeInfo i1bProperTabularTypeArray = attrInfo(info1b, "ProperTabularTypeArray");
        assertEquals(TypeHelper.cache("i1bProperTabularTypeArray", i1bProperTabularTypeArray.getType(), null).type(), Map[].class);
        assertEquals(TypeHelper.cache("i1bProperTabularTypeArray", i1bProperTabularTypeArray.getType(), null).typeName(), "[Ljava.util.Map;");
        assertNull(i1bProperTabularTypeArray.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
        assertNull(TypeHelper.cache("i1bProperTabularTypeArray", i1bProperTabularTypeArray.getType(), null).openType());
        MBeanAttributeInfo i1bList = attrInfo(info1b, "List");
        assertEquals(TypeHelper.cache("i1bList", i1bList.getType(), null).type(), List.class);
        assertEquals(TypeHelper.cache("i1bList", i1bList.getType(), null).typeName(), "java.util.List");
        assertNull(TypeHelper.cache("i1bList", i1bList.getType(), null).openType());
        MBeanAttributeInfo i1bLists = attrInfo(info1b, "Lists");
        assertEquals(TypeHelper.cache("i1bLists", i1bLists.getType(), null).type(), List[].class);
        assertEquals(TypeHelper.cache("i1bLists", i1bLists.getType(), null).typeName(), "[Ljava.util.List;");
        assertNull(TypeHelper.cache("i1bLists", i1bLists.getType(), null).openType());
        MBeanAttributeInfo i1bSet = attrInfo(info1b, "Set");
        assertEquals(TypeHelper.cache("i1bSet", i1bSet.getType(), null).type(), Set.class);
        assertEquals(TypeHelper.cache("i1bSet", i1bSet.getType(), null).typeName(), "java.util.Set");
        assertNull(TypeHelper.cache("i1bSet", i1bSet.getType(), null).openType());
        MBeanAttributeInfo i1bSets = attrInfo(info1b, "Sets");
        assertEquals(TypeHelper.cache("i1bSet", i1bSets.getType(), null).type(), Set[].class);
        assertEquals(TypeHelper.cache("i1bSet", i1bSets.getType(), null).typeName(), "[Ljava.util.Set;");
        assertNull(TypeHelper.cache("i1bSet", i1bSets.getType(), null).openType());
        MBeanAttributeInfo i1bCompositeData = attrInfo(info1b, "CompositeData");
        assertEquals(TypeHelper.cache("i1bCompositeData", i1bCompositeData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1bCompositeData", i1bCompositeData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i1bCompositeData", i1bCompositeData.getType(), null).openType());
        assertFalse(i1bCompositeData instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i1bCompositeDataArray = attrInfo(info1b, "CompositeDataArray");
        assertEquals(TypeHelper.cache("i1bCompositeDataArray", i1bCompositeDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1bCompositeDataArray", i1bCompositeDataArray.getType(), null).typeName(), CompositeData[].class.getName());
        assertNull(TypeHelper.cache("i1bCompositeDataArray", i1bCompositeDataArray.getType(), null).openType());
        assertFalse(i1bCompositeDataArray instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i1bTabularData = attrInfo(info1b, "TabularData");
        assertEquals(TypeHelper.cache("i1bTabularData", i1bTabularData.getType(), null).type(), TabularData.class);
        assertEquals(TypeHelper.cache("i1bTabularData", i1bTabularData.getType(), null).typeName(), TabularData.class.getName());
        assertNull(TypeHelper.cache("i1bTabularData", i1bTabularData.getType(), null).openType());
        assertFalse(i1bTabularData instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i1bTabularDataArray = attrInfo(info1b, "TabularDataArray");
        assertEquals(TypeHelper.cache("i1bTabularDataArray", i1bTabularDataArray.getType(), null).type(), TabularData[].class);
        assertEquals(TypeHelper.cache("i1bTabularDataArray", i1bTabularDataArray.getType(), null).typeName(), TabularData[].class.getName());
        assertNull(TypeHelper.cache("i1bTabularDataArray", i1bTabularDataArray.getType(), null).openType());
        assertFalse(i1bTabularDataArray instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i1bUser = attrInfo(info1b, "User");
        assertEquals(TypeHelper.cache("i1bUser", i1bUser.getType(), null).type(), User.class);
        assertEquals(TypeHelper.cache("i1bUser", i1bUser.getType(), null).typeName(), User.class.getName());
        assertNull(TypeHelper.cache("i1bUser", i1bUser.getType(), null).openType());
        MBeanAttributeInfo i1bUsers = attrInfo(info1b, "Users");
        assertEquals(TypeHelper.cache("i1bUsers", i1bUsers.getType(), null).type(), User[].class);
        assertEquals(TypeHelper.cache("i1bUsers", i1bUsers.getType(), null).typeName(), User[].class.getName());
        assertNull(TypeHelper.cache("i1bUsers", i1bUsers.getType(), null).openType());

        // 2a

        MBeanAttributeInfo i2aShort = attrInfo(info2a, "Short");
        assertEquals(TypeHelper.cache("i2aShort", i2aShort.getType(), null).type(), Short.class);
        assertEquals(TypeHelper.cache("i2aShort", i2aShort.getType(), null).typeName(), "java.lang.Short");
        assertEquals(TypeHelper.cache("i2aShort", i2aShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i2aPrimitiveShort = attrInfo(info2a, "PrimitiveShort");
        assertEquals(TypeHelper.cache("i2aPrimitiveShort", i2aPrimitiveShort.getType(), null).type(), Short.TYPE);
        assertEquals(TypeHelper.cache("i2aPrimitiveShort", i2aPrimitiveShort.getType(), null).typeName(), "short");
        assertEquals(TypeHelper.cache("i2aPrimitiveShort", i2aPrimitiveShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i2aPrimitiveShortArray = attrInfo(info2a, "PrimitiveShortArray");
        assertEquals(TypeHelper.cache("i2aPrimitiveShortArray", i2aPrimitiveShortArray.getType(), null).type(), short[].class);
        assertEquals(TypeHelper.cache("i2aPrimitiveShortArray", i2aPrimitiveShortArray.getType(), null).typeName(), "[S");
        assertEquals(TypeHelper.cache("i2aPrimitiveShortArray", i2aPrimitiveShortArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[].class));
        MBeanAttributeInfo i2aShortArray = attrInfo(info2a, "ShortArray");
        assertEquals(TypeHelper.cache("i2aShortArray", i2aShortArray.getType(), null).type(), Short[].class);
        assertEquals(TypeHelper.cache("i2aShortArray", i2aShortArray.getType(), null).typeName(), "[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i2aShortArray", i2aShortArray.getType(), null).openType(), new ArrayType<>(1, SimpleType.SHORT));
        MBeanAttributeInfo i2aPrimitiveShort2DArray = attrInfo(info2a, "PrimitiveShort2DArray");
        assertEquals(TypeHelper.cache("i2aPrimitiveShort2DArray", i2aPrimitiveShort2DArray.getType(), null).type(), short[][].class);
        assertEquals(TypeHelper.cache("i2aPrimitiveShort2DArray", i2aPrimitiveShort2DArray.getType(), null).typeName(), "[[S");
        assertEquals(TypeHelper.cache("i2aPrimitiveShort2DArray", i2aPrimitiveShort2DArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[][].class));
        MBeanAttributeInfo i2aShort2DArray = attrInfo(info2a, "Short2DArray");
        assertEquals(TypeHelper.cache("i2aShort2DArray", i2aShort2DArray.getType(), null).type(), Short[][].class);
        assertEquals(TypeHelper.cache("i2aShort2DArray", i2aShort2DArray.getType(), null).typeName(), "[[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i2aShort2DArray", i2aShort2DArray.getType(), null).openType(), new ArrayType<>(2, SimpleType.SHORT));

        MBeanAttributeInfo i2aProperTabularType = attrInfo(info2a, "ProperTabularType");
        assertEquals(TypeHelper.cache("i2aProperTabularType", i2aProperTabularType.getType(), null).type(), Map.class);
        assertEquals(TypeHelper.cache("i2aProperTabularType", i2aProperTabularType.getType(), null).typeName(), Map.class.getName());
        assertNull(i2aProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
        assertNull(TypeHelper.cache("i2aProperTabularType", i2aProperTabularType.getType(), null).openType());
        MBeanAttributeInfo i2aProperTabularTypeArray = attrInfo(info2a, "ProperTabularTypeArray");
        assertEquals(TypeHelper.cache("i2aProperTabularTypeArray", i2aProperTabularTypeArray.getType(), null).type(), Map[].class);
        assertEquals(TypeHelper.cache("i2aProperTabularTypeArray", i2aProperTabularTypeArray.getType(), null).typeName(), "[Ljava.util.Map;");
        assertNull(i2aProperTabularTypeArray.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
        assertNull(TypeHelper.cache("i2aProperTabularTypeArray", i2aProperTabularTypeArray.getType(), null).openType());
        MBeanAttributeInfo i2aList = attrInfo(info2a, "List");
        assertEquals(TypeHelper.cache("i2aList", i2aList.getType(), null).type(), List.class);
        assertEquals(TypeHelper.cache("i2aList", i2aList.getType(), null).typeName(), "java.util.List");
        assertNull(TypeHelper.cache("i2aList", i2aList.getType(), null).openType());
        MBeanAttributeInfo i2aLists = attrInfo(info2a, "Lists");
        assertEquals(TypeHelper.cache("i2aLists", i2aLists.getType(), null).type(), List[].class);
        assertEquals(TypeHelper.cache("i2aLists", i2aLists.getType(), null).typeName(), "[Ljava.util.List;");
        assertNull(TypeHelper.cache("i2aLists", i2aLists.getType(), null).openType());
        MBeanAttributeInfo i2aSet = attrInfo(info2a, "Set");
        assertEquals(TypeHelper.cache("i2aSet", i2aSet.getType(), null).type(), Set.class);
        assertEquals(TypeHelper.cache("i2aSet", i2aSet.getType(), null).typeName(), "java.util.Set");
        assertNull(TypeHelper.cache("i2aSet", i2aSet.getType(), null).openType());
        MBeanAttributeInfo i2aSets = attrInfo(info2a, "Sets");
        assertEquals(TypeHelper.cache("i2aSet", i2aSets.getType(), null).type(), Set[].class);
        assertEquals(TypeHelper.cache("i2aSet", i2aSets.getType(), null).typeName(), "[Ljava.util.Set;");
        assertNull(TypeHelper.cache("i2aSet", i2aSets.getType(), null).openType());
        MBeanAttributeInfo i2aCompositeData = attrInfo(info2a, "CompositeData");
        assertEquals(TypeHelper.cache("i2aCompositeData", i2aCompositeData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i2aCompositeData", i2aCompositeData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i2aCompositeData", i2aCompositeData.getType(), null).openType());
        assertFalse(i2aCompositeData instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2aCompositeDataArray = attrInfo(info2a, "CompositeDataArray");
        assertEquals(TypeHelper.cache("i2aCompositeDataArray", i2aCompositeDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i2aCompositeDataArray", i2aCompositeDataArray.getType(), null).typeName(), CompositeData[].class.getName());
        assertNull(TypeHelper.cache("i2aCompositeDataArray", i2aCompositeDataArray.getType(), null).openType());
        assertFalse(i2aCompositeDataArray instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2aTabularData = attrInfo(info2a, "TabularData");
        assertEquals(TypeHelper.cache("i2aTabularData", i2aTabularData.getType(), null).type(), TabularData.class);
        assertEquals(TypeHelper.cache("i2aTabularData", i2aTabularData.getType(), null).typeName(), TabularData.class.getName());
        assertNull(TypeHelper.cache("i2aTabularData", i2aTabularData.getType(), null).openType());
        assertFalse(i2aTabularData instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2aTabularDataArray = attrInfo(info2a, "TabularDataArray");
        assertEquals(TypeHelper.cache("i2aTabularDataArray", i2aTabularDataArray.getType(), null).type(), TabularData[].class);
        assertEquals(TypeHelper.cache("i2aTabularDataArray", i2aTabularDataArray.getType(), null).typeName(), TabularData[].class.getName());
        assertNull(TypeHelper.cache("i2aTabularDataArray", i2aTabularDataArray.getType(), null).openType());
        assertFalse(i2aTabularDataArray instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2aUser = attrInfo(info2a, "User");
        assertEquals(TypeHelper.cache("i2aUser", i2aUser.getType(), null).type(), User.class);
        assertEquals(TypeHelper.cache("i2aUser", i2aUser.getType(), null).typeName(), User.class.getName());
        assertNull(TypeHelper.cache("i2aUser", i2aUser.getType(), null).openType());
        MBeanAttributeInfo i2aUsers = attrInfo(info2a, "Users");
        assertEquals(TypeHelper.cache("i2aUsers", i2aUsers.getType(), null).type(), User[].class);
        assertEquals(TypeHelper.cache("i2aUsers", i2aUsers.getType(), null).typeName(), User[].class.getName());
        assertNull(TypeHelper.cache("i2aUsers", i2aUsers.getType(), null).openType());
        MBeanAttributeInfo i2aCollection = attrInfo(info2a, "Collection");
        assertEquals(TypeHelper.cache("i2aCollection", i2aCollection.getType(), null).type(), Collection.class);
        assertEquals(TypeHelper.cache("i2aCollection", i2aCollection.getType(), null).typeName(), Collection.class.getName());
        assertNull(TypeHelper.cache("i2aCollection", i2aCollection.getType(), null).openType());

        // 2b

        MBeanAttributeInfo i2bShort = attrInfo(info2b, "Short");
        assertEquals(TypeHelper.cache("i2bShort", i2bShort.getType(), null).type(), Short.class);
        assertEquals(TypeHelper.cache("i2bShort", i2bShort.getType(), null).typeName(), "java.lang.Short");
        assertEquals(TypeHelper.cache("i2bShort", i2bShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i2bPrimitiveShort = attrInfo(info2b, "PrimitiveShort");
        assertEquals(TypeHelper.cache("i2bPrimitiveShort", i2bPrimitiveShort.getType(), null).type(), Short.TYPE);
        assertEquals(TypeHelper.cache("i2bPrimitiveShort", i2bPrimitiveShort.getType(), null).typeName(), "short");
        assertEquals(TypeHelper.cache("i2bPrimitiveShort", i2bPrimitiveShort.getType(), null).openType(), SimpleType.SHORT);
        MBeanAttributeInfo i2bPrimitiveShortArray = attrInfo(info2b, "PrimitiveShortArray");
        assertEquals(TypeHelper.cache("i2bPrimitiveShortArray", i2bPrimitiveShortArray.getType(), null).type(), short[].class);
        assertEquals(TypeHelper.cache("i2bPrimitiveShortArray", i2bPrimitiveShortArray.getType(), null).typeName(), "[S");
        assertEquals(TypeHelper.cache("i2bPrimitiveShortArray", i2bPrimitiveShortArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[].class));
        MBeanAttributeInfo i2bShortArray = attrInfo(info2b, "ShortArray");
        assertEquals(TypeHelper.cache("i2bShortArray", i2bShortArray.getType(), null).type(), Short[].class);
        assertEquals(TypeHelper.cache("i2bShortArray", i2bShortArray.getType(), null).typeName(), "[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i2bShortArray", i2bShortArray.getType(), null).openType(), new ArrayType<>(1, SimpleType.SHORT));
        MBeanAttributeInfo i2bPrimitiveShort2DArray = attrInfo(info2b, "PrimitiveShort2DArray");
        assertEquals(TypeHelper.cache("i2bPrimitiveShort2DArray", i2bPrimitiveShort2DArray.getType(), null).type(), short[][].class);
        assertEquals(TypeHelper.cache("i2bPrimitiveShort2DArray", i2bPrimitiveShort2DArray.getType(), null).typeName(), "[[S");
        assertEquals(TypeHelper.cache("i2bPrimitiveShort2DArray", i2bPrimitiveShort2DArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[][].class));
        MBeanAttributeInfo i2bShort2DArray = attrInfo(info2b, "Short2DArray");
        assertEquals(TypeHelper.cache("i2bShort2DArray", i2bShort2DArray.getType(), null).type(), Short[][].class);
        assertEquals(TypeHelper.cache("i2bShort2DArray", i2bShort2DArray.getType(), null).typeName(), "[[Ljava.lang.Short;");
        assertEquals(TypeHelper.cache("i2bShort2DArray", i2bShort2DArray.getType(), null).openType(), new ArrayType<>(2, SimpleType.SHORT));

        MBeanAttributeInfo i2bProperTabularType = attrInfo(info2b, "ProperTabularType");
        assertEquals(TypeHelper.cache("i2bProperTabularType", i2bProperTabularType.getType(), null).type(), Map.class);
        assertEquals(TypeHelper.cache("i2bProperTabularType", i2bProperTabularType.getType(), null).typeName(), Map.class.getName());
        assertNull(i2bProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
        assertNull(TypeHelper.cache("i2bProperTabularType", i2bProperTabularType.getType(), null).openType());
        MBeanAttributeInfo i2bProperTabularTypeArray = attrInfo(info2b, "ProperTabularTypeArray");
        assertEquals(TypeHelper.cache("i2bProperTabularTypeArray", i2bProperTabularTypeArray.getType(), null).type(), Map[].class);
        assertEquals(TypeHelper.cache("i2bProperTabularTypeArray", i2bProperTabularTypeArray.getType(), null).typeName(), "[Ljava.util.Map;");
        assertNull(i2bProperTabularTypeArray.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
        assertNull(TypeHelper.cache("i2bProperTabularTypeArray", i2bProperTabularTypeArray.getType(), null).openType());
        MBeanAttributeInfo i2bList = attrInfo(info2b, "List");
        assertEquals(TypeHelper.cache("i2bList", i2bList.getType(), null).type(), List.class);
        assertEquals(TypeHelper.cache("i2bList", i2bList.getType(), null).typeName(), "java.util.List");
        assertNull(TypeHelper.cache("i2bList", i2bList.getType(), null).openType());
        MBeanAttributeInfo i2bLists = attrInfo(info2b, "Lists");
        assertEquals(TypeHelper.cache("i2bLists", i2bLists.getType(), null).type(), List[].class);
        assertEquals(TypeHelper.cache("i2bLists", i2bLists.getType(), null).typeName(), "[Ljava.util.List;");
        assertNull(TypeHelper.cache("i2bLists", i2bLists.getType(), null).openType());
        MBeanAttributeInfo i2bSet = attrInfo(info2b, "Set");
        assertEquals(TypeHelper.cache("i2bSet", i2bSet.getType(), null).type(), Set.class);
        assertEquals(TypeHelper.cache("i2bSet", i2bSet.getType(), null).typeName(), "java.util.Set");
        assertNull(TypeHelper.cache("i2bSet", i2bSet.getType(), null).openType());
        MBeanAttributeInfo i2bSets = attrInfo(info2b, "Sets");
        assertEquals(TypeHelper.cache("i2bSet", i2bSets.getType(), null).type(), Set[].class);
        assertEquals(TypeHelper.cache("i2bSet", i2bSets.getType(), null).typeName(), "[Ljava.util.Set;");
        assertNull(TypeHelper.cache("i2bSet", i2bSets.getType(), null).openType());
        MBeanAttributeInfo i2bCompositeData = attrInfo(info2b, "CompositeData");
        assertEquals(TypeHelper.cache("i2bCompositeData", i2bCompositeData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i2bCompositeData", i2bCompositeData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i2bCompositeData", i2bCompositeData.getType(), null).openType());
        assertFalse(i2bCompositeData instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2bCompositeDataArray = attrInfo(info2b, "CompositeDataArray");
        assertEquals(TypeHelper.cache("i2bCompositeDataArray", i2bCompositeDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i2bCompositeDataArray", i2bCompositeDataArray.getType(), null).typeName(), CompositeData[].class.getName());
        assertNull(TypeHelper.cache("i2bCompositeDataArray", i2bCompositeDataArray.getType(), null).openType());
        assertFalse(i2bCompositeDataArray instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2bTabularData = attrInfo(info2b, "TabularData");
        assertEquals(TypeHelper.cache("i2bTabularData", i2bTabularData.getType(), null).type(), TabularData.class);
        assertEquals(TypeHelper.cache("i2bTabularData", i2bTabularData.getType(), null).typeName(), TabularData.class.getName());
        assertNull(TypeHelper.cache("i2bTabularData", i2bTabularData.getType(), null).openType());
        assertFalse(i2bTabularData instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2bTabularDataArray = attrInfo(info2b, "TabularDataArray");
        assertEquals(TypeHelper.cache("i2bTabularDataArray", i2bTabularDataArray.getType(), null).type(), TabularData[].class);
        assertEquals(TypeHelper.cache("i2bTabularDataArray", i2bTabularDataArray.getType(), null).typeName(), TabularData[].class.getName());
        assertNull(TypeHelper.cache("i2bTabularDataArray", i2bTabularDataArray.getType(), null).openType());
        assertFalse(i2bTabularDataArray instanceof OpenMBeanAttributeInfo);
        MBeanAttributeInfo i2bUser = attrInfo(info2b, "User");
        assertEquals(TypeHelper.cache("i2bUser", i2bUser.getType(), null).type(), User.class);
        assertEquals(TypeHelper.cache("i2bUser", i2bUser.getType(), null).typeName(), User.class.getName());
        assertNull(TypeHelper.cache("i2bUser", i2bUser.getType(), null).openType());
        MBeanAttributeInfo i2bUsers = attrInfo(info2b, "Users");
        assertEquals(TypeHelper.cache("i2bUsers", i2bUsers.getType(), null).type(), User[].class);
        assertEquals(TypeHelper.cache("i2bUsers", i2bUsers.getType(), null).typeName(), User[].class.getName());
        assertNull(TypeHelper.cache("i2bUsers", i2bUsers.getType(), null).openType());
        MBeanAttributeInfo i2bCollection = attrInfo(info2b, "Collection");
        assertEquals(TypeHelper.cache("i2bCollection", i2bCollection.getType(), null).type(), Collection.class);
        assertEquals(TypeHelper.cache("i2bCollection", i2bCollection.getType(), null).typeName(), Collection.class.getName());
        assertNull(TypeHelper.cache("i2bCollection", i2bCollection.getType(), null).openType());
    }

    private MBeanAttributeInfo attrInfo(MBeanInfo info, String name) {
        return Arrays.stream(info.getAttributes()).filter(attr -> attr.getName().equals(name)).findFirst().orElseThrow();
    }

}
