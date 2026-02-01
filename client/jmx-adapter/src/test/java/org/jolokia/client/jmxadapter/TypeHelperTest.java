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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.StandardMBean;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanInfo;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;

import org.jolokia.client.jmxadapter.beans.Example1;
import org.jolokia.client.jmxadapter.beans.Example1MXBean;
import org.jolokia.client.jmxadapter.beans.Example2;
import org.jolokia.client.jmxadapter.beans.Example2MBean;
import org.jolokia.client.jmxadapter.beans.User;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TypeHelperTest {

    private static ObjectToJsonConverter toJsonConverter;

    @BeforeClass
    public void setup() {
        ObjectToObjectConverter objectToObjectConverter = new ObjectToObjectConverter();
        ObjectToOpenTypeConverter objectToOpenTypeConverter = new ObjectToOpenTypeConverter(objectToObjectConverter, true);
        toJsonConverter = new ObjectToJsonConverter(objectToObjectConverter, objectToOpenTypeConverter, null);
        TypeHelper.converter = objectToObjectConverter;
        TypeHelper.CACHE.clear();
    }

    @Test
    public void uniqueTypeCaching() throws OpenDataException, ReflectionException {
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
    public void ambiguousTypeCaching() throws Exception {
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
    @SuppressWarnings("unchecked")
    public void typeCachingFromMBeanInfo() throws Exception {
        MBeanServer jmx = ManagementFactory.getPlatformMBeanServer();

        ObjectName example1a = new ObjectName("jolokia:test=TypeHelperTest,name=example1a");
        ObjectName example1b = new ObjectName("jolokia:test=TypeHelperTest,name=example1b");
        ObjectName example2a = new ObjectName("jolokia:test=TypeHelperTest,name=example2a");
        ObjectName example2b = new ObjectName("jolokia:test=TypeHelperTest,name=example2b");

        // 1 wrapped in com.sun.jmx.mbeanserver.MXBeanSupport
        for (ObjectName name: List.of(example1a, example1b, example2a, example2b)) {
            if (jmx.isRegistered(name)) {
                jmx.unregisterMBean(name);
            }
        }
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

        // The below is NOT AI-generated

        // 1a - MXBean

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
        assertEquals(i1aProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), Example1.typeOfMXTabularDataAttribute());
        assertNull(TypeHelper.cache("i1aProperTabularType", i1aProperTabularType.getType(), null).openType());
        MBeanAttributeInfo i1aProperTabularTypeArray = attrInfo(info1a, "ProperTabularTypeArray");
        assertEquals(TypeHelper.cache("i1aProperTabularTypeArray", i1aProperTabularTypeArray.getType(), null).type(), TabularData[].class);
        assertEquals(TypeHelper.cache("i1aProperTabularTypeArray", i1aProperTabularTypeArray.getType(), null).typeName(), "[Ljavax.management.openmbean.TabularData;");
        assertEquals(i1aProperTabularTypeArray.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), new ArrayType<>(1, Example1.typeOfMXTabularDataAttribute()));
        assertNull(TypeHelper.cache("i1aProperTabularTypeArray", i1aProperTabularTypeArray.getType(), null).openType());
        MBeanAttributeInfo i1aAlmostProperTabularType = attrInfo(info1a, "AlmostProperTabularType");
        assertEquals(TypeHelper.cache("i1aAlmostProperTabularType", i1aAlmostProperTabularType.getType(), null).type(), TabularData.class);
        assertEquals(TypeHelper.cache("i1aAlmostProperTabularType", i1aAlmostProperTabularType.getType(), null).typeName(), TabularData.class.getName());
        assertEquals(i1aAlmostProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), Example1.typeOfAlmostProperTabularDataAttribute());
        assertNull(TypeHelper.cache("i1aAlmostProperTabularType", i1aAlmostProperTabularType.getType(), null).openType());
        MBeanAttributeInfo i1aComplexKeyedTabularType = attrInfo(info1a, "ComplexKeyedTabularType");
        assertEquals(TypeHelper.cache("i1aComplexKeyedTabularType", i1aComplexKeyedTabularType.getType(), null).type(), TabularData.class);
        assertEquals(TypeHelper.cache("i1aComplexKeyedTabularType", i1aComplexKeyedTabularType.getType(), null).typeName(), TabularData.class.getName());
        assertEquals(i1aComplexKeyedTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD), Example1.typeOfComplexKeyedTabularDataAttribute());
        assertNull(TypeHelper.cache("i1aComplexKeyedTabularType", i1aComplexKeyedTabularType.getType(), null).openType());

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
        ArrayType<CompositeData> i1aArrayType = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aCompositeDataArray).getOpenType();
        assertEquals(TypeHelper.cache("i1aCompositeDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aCompositeDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).typeName(), CompositeData[].class.getName());
        assertEquals(TypeHelper.cache("i1aCompositeDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).openType(), i1aArrayType);
        i1aCompositeType = (CompositeType) i1aArrayType.getElementOpenType();
        assertEquals(i1aCompositeType.keySet().size(), 1);
        assertTrue(i1aCompositeType.containsKey("compositeType"));
        i1aCompositeData = attrInfo(info1a, "CompositeMXData");
        assertEquals(TypeHelper.cache("i1aCompositeMXData", i1aCompositeData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aCompositeMXData", i1aCompositeData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i1aCompositeMXData", i1aCompositeData.getType(), null).openType());
        i1aCompositeType = (CompositeType) ((OpenMBeanAttributeInfo) i1aCompositeData).getOpenType();
        assertEquals(TypeHelper.cache("i1aCompositeMXData2", i1aCompositeData.getType(), i1aCompositeType).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aCompositeMXData2", i1aCompositeData.getType(), i1aCompositeType).typeName(), CompositeData.class.getName());
        assertEquals(TypeHelper.cache("i1aCompositeMXData2", i1aCompositeData.getType(), i1aCompositeType).openType(), i1aCompositeType);
        assertEquals(i1aCompositeType.keySet().size(), 1);
        assertTrue(i1aCompositeType.containsKey("compositeType"));
        i1aCompositeDataArray = attrInfo(info1a, "CompositeMXDataArray");
        assertEquals(TypeHelper.cache("i1aCompositeMXDataArray", i1aCompositeDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aCompositeMXDataArray", i1aCompositeDataArray.getType(), null).typeName(), "[Ljavax.management.openmbean.CompositeData;");
        assertNull(TypeHelper.cache("i1aCompositeMXDataArray", i1aCompositeDataArray.getType(), null).openType());
        i1aArrayType = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aCompositeDataArray).getOpenType();
        assertEquals(TypeHelper.cache("i1aCompositeMXDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aCompositeMXDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).typeName(), CompositeData[].class.getName());
        assertEquals(TypeHelper.cache("i1aCompositeMXDataArray2", i1aCompositeDataArray.getType(), i1aArrayType).openType(), i1aArrayType);
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
        ArrayType<CompositeData> i1aArrayType2 = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aTabularDataArray).getOpenType();
        assertEquals(TypeHelper.cache("i1aTabularDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).type(), CompositeData[].class);
        // we do some Jolokia special conversion in org.jolokia.converter.object.OpenTypeHelper.toJSON(javax.management.openmbean.ArrayType<?>, javax.management.MBeanFeatureInfo) too
        assertEquals(TypeHelper.cache("i1aTabularDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).typeName(), /*CompositeData[].class.getName()*/TabularData[].class.getName());
        assertEquals(TypeHelper.cache("i1aTabularDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).openType(), i1aArrayType2);
        i1aCompositeType = (CompositeType) i1aArrayType2.getElementOpenType();
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("tabularType"));
        assertTrue(i1aCompositeType.containsKey("empty"));
        i1aTabularData = attrInfo(info1a, "TabularMXData");
        assertEquals(TypeHelper.cache("i1aTabularMXData", i1aTabularData.getType(), null).type(), CompositeData.class);
        assertEquals(TypeHelper.cache("i1aTabularMXData", i1aTabularData.getType(), null).typeName(), CompositeData.class.getName());
        assertNull(TypeHelper.cache("i1aTabularMXData", i1aTabularData.getType(), null).openType());
        i1aCompositeType = (CompositeType) ((OpenMBeanAttributeInfo) i1aTabularData).getOpenType();
        assertEquals(TypeHelper.cache("i1aTabularMXData2", i1aTabularData.getType(), i1aCompositeType).type(), CompositeData.class);
        // typeName comes from OpenType, so it's actually TabularData, not CompositeData
        assertEquals(TypeHelper.cache("i1aTabularMXData2", i1aTabularData.getType(), i1aCompositeType).typeName(), TabularData.class.getName());
        assertEquals(TypeHelper.cache("i1aTabularMXData2", i1aTabularData.getType(), i1aCompositeType).openType(), i1aCompositeType);
        assertEquals(i1aCompositeType.keySet().size(), 2);
        assertTrue(i1aCompositeType.containsKey("tabularType"));
        assertTrue(i1aCompositeType.containsKey("empty"));
        i1aTabularDataArray = attrInfo(info1a, "TabularMXDataArray");
        assertEquals(TypeHelper.cache("i1aTabularMXDataArray", i1aTabularDataArray.getType(), null).type(), CompositeData[].class);
        assertEquals(TypeHelper.cache("i1aTabularMXDataArray", i1aTabularDataArray.getType(), null).typeName(), "[Ljavax.management.openmbean.CompositeData;");
        assertNull(TypeHelper.cache("i1aTabularMXDataArray", i1aTabularDataArray.getType(), null).openType());
        i1aArrayType2 = (ArrayType<CompositeData>) ((OpenMBeanAttributeInfo) i1aTabularDataArray).getOpenType();
        assertEquals(TypeHelper.cache("i1aTabularMXDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).type(), CompositeData[].class);
        // we do some Jolokia special conversion in org.jolokia.converter.object.OpenTypeHelper.toJSON(javax.management.openmbean.ArrayType<?>, javax.management.MBeanFeatureInfo) too
        assertEquals(TypeHelper.cache("i1aTabularMXDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).typeName(), /*CompositeData[].class.getName()*/TabularData[].class.getName());
        assertEquals(TypeHelper.cache("i1aTabularMXDataArray2", i1aTabularDataArray.getType(), i1aArrayType2).openType(), i1aArrayType2);
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

        // 1b, 2a, 2b - all non-MXBeans

        for (MBeanInfo mbInfo : new MBeanInfo[] { info1b, info2a, info2b }) {
            MBeanAttributeInfo iShort = attrInfo(mbInfo, "Short");
            assertEquals(TypeHelper.cache("iShort", iShort.getType(), null).type(), Short.class);
            assertEquals(TypeHelper.cache("iShort", iShort.getType(), null).typeName(), "java.lang.Short");
            assertEquals(TypeHelper.cache("iShort", iShort.getType(), null).openType(), SimpleType.SHORT);
            MBeanAttributeInfo iPrimitiveShort = attrInfo(mbInfo, "PrimitiveShort");
            assertEquals(TypeHelper.cache("iPrimitiveShort", iPrimitiveShort.getType(), null).type(), Short.TYPE);
            assertEquals(TypeHelper.cache("iPrimitiveShort", iPrimitiveShort.getType(), null).typeName(), "short");
            assertEquals(TypeHelper.cache("iPrimitiveShort", iPrimitiveShort.getType(), null).openType(), SimpleType.SHORT);
            MBeanAttributeInfo iPrimitiveShortArray = attrInfo(mbInfo, "PrimitiveShortArray");
            assertEquals(TypeHelper.cache("iPrimitiveShortArray", iPrimitiveShortArray.getType(), null).type(), short[].class);
            assertEquals(TypeHelper.cache("iPrimitiveShortArray", iPrimitiveShortArray.getType(), null).typeName(), "[S");
            assertEquals(TypeHelper.cache("iPrimitiveShortArray", iPrimitiveShortArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[].class));
            MBeanAttributeInfo iShortArray = attrInfo(mbInfo, "ShortArray");
            assertEquals(TypeHelper.cache("iShortArray", iShortArray.getType(), null).type(), Short[].class);
            assertEquals(TypeHelper.cache("iShortArray", iShortArray.getType(), null).typeName(), "[Ljava.lang.Short;");
            assertEquals(TypeHelper.cache("iShortArray", iShortArray.getType(), null).openType(), new ArrayType<>(1, SimpleType.SHORT));
            MBeanAttributeInfo iPrimitiveShort2DArray = attrInfo(mbInfo, "PrimitiveShort2DArray");
            assertEquals(TypeHelper.cache("iPrimitiveShort2DArray", iPrimitiveShort2DArray.getType(), null).type(), short[][].class);
            assertEquals(TypeHelper.cache("iPrimitiveShort2DArray", iPrimitiveShort2DArray.getType(), null).typeName(), "[[S");
            assertEquals(TypeHelper.cache("iPrimitiveShort2DArray", iPrimitiveShort2DArray.getType(), null).openType(), ArrayType.getPrimitiveArrayType(short[][].class));
            MBeanAttributeInfo iShort2DArray = attrInfo(mbInfo, "Short2DArray");
            assertEquals(TypeHelper.cache("iShort2DArray", iShort2DArray.getType(), null).type(), Short[][].class);
            assertEquals(TypeHelper.cache("iShort2DArray", iShort2DArray.getType(), null).typeName(), "[[Ljava.lang.Short;");
            assertEquals(TypeHelper.cache("iShort2DArray", iShort2DArray.getType(), null).openType(), new ArrayType<>(2, SimpleType.SHORT));

            MBeanAttributeInfo iProperTabularType = attrInfo(mbInfo, "ProperTabularType");
            assertEquals(TypeHelper.cache("iProperTabularType", iProperTabularType.getType(), null).type(), Map.class);
            assertEquals(TypeHelper.cache("iProperTabularType", iProperTabularType.getType(), null).typeName(), Map.class.getName());
            assertNull(iProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
            assertNull(TypeHelper.cache("iProperTabularType", iProperTabularType.getType(), null).openType());
            MBeanAttributeInfo iProperTabularTypeArray = attrInfo(mbInfo, "ProperTabularTypeArray");
            assertEquals(TypeHelper.cache("iProperTabularTypeArray", iProperTabularTypeArray.getType(), null).type(), Map[].class);
            assertEquals(TypeHelper.cache("iProperTabularTypeArray", iProperTabularTypeArray.getType(), null).typeName(), "[Ljava.util.Map;");
            assertNull(iProperTabularTypeArray.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
            assertNull(TypeHelper.cache("iProperTabularTypeArray", iProperTabularTypeArray.getType(), null).openType());
            MBeanAttributeInfo iAlmostProperTabularType = attrInfo(mbInfo, "AlmostProperTabularType");
            assertEquals(TypeHelper.cache("iAlmostProperTabularType", iAlmostProperTabularType.getType(), null).type(), Map.class);
            assertEquals(TypeHelper.cache("iAlmostProperTabularType", iAlmostProperTabularType.getType(), null).typeName(), Map.class.getName());
            assertNull(iAlmostProperTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
            assertNull(TypeHelper.cache("iAlmostProperTabularType", iAlmostProperTabularType.getType(), null).openType());
            MBeanAttributeInfo iComplexKeyedTabularType = attrInfo(mbInfo, "ComplexKeyedTabularType");
            assertEquals(TypeHelper.cache("iComplexKeyedTabularType", iComplexKeyedTabularType.getType(), null).type(), Map.class);
            assertEquals(TypeHelper.cache("iComplexKeyedTabularType", iComplexKeyedTabularType.getType(), null).typeName(), Map.class.getName());
            assertNull(iComplexKeyedTabularType.getDescriptor().getFieldValue(JMX.OPEN_TYPE_FIELD));
            assertNull(TypeHelper.cache("iComplexKeyedTabularType", iComplexKeyedTabularType.getType(), null).openType());

            MBeanAttributeInfo iList = attrInfo(mbInfo, "List");
            assertEquals(TypeHelper.cache("iList", iList.getType(), null).type(), List.class);
            assertEquals(TypeHelper.cache("iList", iList.getType(), null).typeName(), "java.util.List");
            assertNull(TypeHelper.cache("iList", iList.getType(), null).openType());
            MBeanAttributeInfo iLists = attrInfo(mbInfo, "Lists");
            assertEquals(TypeHelper.cache("iLists", iLists.getType(), null).type(), List[].class);
            assertEquals(TypeHelper.cache("iLists", iLists.getType(), null).typeName(), "[Ljava.util.List;");
            assertNull(TypeHelper.cache("iLists", iLists.getType(), null).openType());
            MBeanAttributeInfo iSet = attrInfo(mbInfo, "Set");
            assertEquals(TypeHelper.cache("iSet", iSet.getType(), null).type(), Set.class);
            assertEquals(TypeHelper.cache("iSet", iSet.getType(), null).typeName(), "java.util.Set");
            assertNull(TypeHelper.cache("iSet", iSet.getType(), null).openType());
            MBeanAttributeInfo iSets = attrInfo(mbInfo, "Sets");
            assertEquals(TypeHelper.cache("iSet", iSets.getType(), null).type(), Set[].class);
            assertEquals(TypeHelper.cache("iSet", iSets.getType(), null).typeName(), "[Ljava.util.Set;");
            assertNull(TypeHelper.cache("iSet", iSets.getType(), null).openType());
            MBeanAttributeInfo iCompositeData = attrInfo(mbInfo, "CompositeData");
            assertEquals(TypeHelper.cache("iCompositeData", iCompositeData.getType(), null).type(), CompositeData.class);
            assertEquals(TypeHelper.cache("iCompositeData", iCompositeData.getType(), null).typeName(), CompositeData.class.getName());
            assertNull(TypeHelper.cache("iCompositeData", iCompositeData.getType(), null).openType());
            assertFalse(iCompositeData instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iCompositeDataArray = attrInfo(mbInfo, "CompositeDataArray");
            assertEquals(TypeHelper.cache("iCompositeDataArray", iCompositeDataArray.getType(), null).type(), CompositeData[].class);
            assertEquals(TypeHelper.cache("iCompositeDataArray", iCompositeDataArray.getType(), null).typeName(), CompositeData[].class.getName());
            assertNull(TypeHelper.cache("iCompositeDataArray", iCompositeDataArray.getType(), null).openType());
            assertFalse(iCompositeDataArray instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iCompositeMXData = attrInfo(mbInfo, "CompositeMXData");
            assertEquals(TypeHelper.cache("iCompositeMXData", iCompositeMXData.getType(), null).type(), CompositeData.class);
            assertEquals(TypeHelper.cache("iCompositeMXData", iCompositeMXData.getType(), null).typeName(), CompositeData.class.getName());
            assertNull(TypeHelper.cache("iCompositeMXData", iCompositeMXData.getType(), null).openType());
            assertFalse(iCompositeMXData instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iCompositeMXDataArray = attrInfo(mbInfo, "CompositeMXDataArray");
            assertEquals(TypeHelper.cache("iCompositeMXDataArray", iCompositeMXDataArray.getType(), null).type(), CompositeData[].class);
            assertEquals(TypeHelper.cache("iCompositeMXDataArray", iCompositeMXDataArray.getType(), null).typeName(), CompositeData[].class.getName());
            assertNull(TypeHelper.cache("iCompositeMXDataArray", iCompositeMXDataArray.getType(), null).openType());
            assertFalse(iCompositeMXDataArray instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iTabularData = attrInfo(mbInfo, "TabularData");
            assertEquals(TypeHelper.cache("iTabularData", iTabularData.getType(), null).type(), TabularData.class);
            assertEquals(TypeHelper.cache("iTabularData", iTabularData.getType(), null).typeName(), TabularData.class.getName());
            assertNull(TypeHelper.cache("iTabularData", iTabularData.getType(), null).openType());
            assertFalse(iTabularData instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iTabularDataArray = attrInfo(mbInfo, "TabularDataArray");
            assertEquals(TypeHelper.cache("iTabularDataArray", iTabularDataArray.getType(), null).type(), TabularData[].class);
            assertEquals(TypeHelper.cache("iTabularDataArray", iTabularDataArray.getType(), null).typeName(), TabularData[].class.getName());
            assertNull(TypeHelper.cache("iTabularDataArray", iTabularDataArray.getType(), null).openType());
            assertFalse(iTabularDataArray instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iTabularMXData = attrInfo(mbInfo, "TabularMXData");
            assertEquals(TypeHelper.cache("iTabularMXData", iTabularMXData.getType(), null).type(), TabularData.class);
            assertEquals(TypeHelper.cache("iTabularMXData", iTabularMXData.getType(), null).typeName(), TabularData.class.getName());
            assertNull(TypeHelper.cache("iTabularMXData", iTabularMXData.getType(), null).openType());
            assertFalse(iTabularMXData instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iTabularMXDataArray = attrInfo(mbInfo, "TabularMXDataArray");
            assertEquals(TypeHelper.cache("iTabularMXDataArray", iTabularMXDataArray.getType(), null).type(), TabularData[].class);
            assertEquals(TypeHelper.cache("iTabularMXDataArray", iTabularMXDataArray.getType(), null).typeName(), TabularData[].class.getName());
            assertNull(TypeHelper.cache("iTabularMXDataArray", iTabularMXDataArray.getType(), null).openType());
            assertFalse(iTabularMXDataArray instanceof OpenMBeanAttributeInfo);
            MBeanAttributeInfo iUser = attrInfo(mbInfo, "User");
            assertEquals(TypeHelper.cache("iUser", iUser.getType(), null).type(), User.class);
            assertEquals(TypeHelper.cache("iUser", iUser.getType(), null).typeName(), User.class.getName());
            assertNull(TypeHelper.cache("iUser", iUser.getType(), null).openType());
            MBeanAttributeInfo iUsers = attrInfo(mbInfo, "Users");
            assertEquals(TypeHelper.cache("iUsers", iUsers.getType(), null).type(), User[].class);
            assertEquals(TypeHelper.cache("iUsers", iUsers.getType(), null).typeName(), User[].class.getName());
            assertNull(TypeHelper.cache("iUsers", iUsers.getType(), null).openType());
        }
    }

    @Test
    public void detectSimpleTypes() throws Exception {
        OpenType<?> openType;
        ObjectName name = new ObjectName("a:b=1");
        openType = TypeHelper.buildOpenType(String.class.getName(), "Hello");
        assertTrue(openType instanceof SimpleType);
        assertEquals(openType.getClassName(), String.class.getName());
        assertSame(openType, SimpleType.STRING);

        assertSame(TypeHelper.buildOpenType(BigDecimal.class.getName(), BigDecimal.TEN), SimpleType.BIGDECIMAL);
        assertSame(TypeHelper.buildOpenType(BigInteger.class.getName(), BigInteger.TEN), SimpleType.BIGINTEGER);
        assertSame(TypeHelper.buildOpenType(Long.class.getName(), 10L), SimpleType.LONG);
        assertSame(TypeHelper.buildOpenType(Integer.class.getName(), 10), SimpleType.INTEGER);
        assertSame(TypeHelper.buildOpenType(Short.class.getName(), (short) 10), SimpleType.SHORT);
        assertSame(TypeHelper.buildOpenType(Byte.class.getName(), (byte) 10), SimpleType.BYTE);
        assertSame(TypeHelper.buildOpenType(long.class.getName(), 10L), SimpleType.LONG);
        assertSame(TypeHelper.buildOpenType(int.class.getName(), 10), SimpleType.INTEGER);
        assertSame(TypeHelper.buildOpenType(short.class.getName(), (short) 10), SimpleType.SHORT);
        assertSame(TypeHelper.buildOpenType(byte.class.getName(), (byte) 10), SimpleType.BYTE);
        assertSame(TypeHelper.buildOpenType(Double.class.getName(), 42.0d), SimpleType.DOUBLE);
        assertSame(TypeHelper.buildOpenType(double.class.getName(), 42.0d), SimpleType.DOUBLE);
        assertSame(TypeHelper.buildOpenType(Float.class.getName(), 42.0f), SimpleType.FLOAT);
        assertSame(TypeHelper.buildOpenType(float.class.getName(), 42.0f), SimpleType.FLOAT);
        assertSame(TypeHelper.buildOpenType(Boolean.class.getName(), Boolean.TRUE), SimpleType.BOOLEAN);
        assertSame(TypeHelper.buildOpenType(boolean.class.getName(), false), SimpleType.BOOLEAN);
        assertSame(TypeHelper.buildOpenType(Character.class.getName(), 'c'), SimpleType.CHARACTER);
        assertSame(TypeHelper.buildOpenType(char.class.getName(), 'c'), SimpleType.CHARACTER);
        assertSame(TypeHelper.buildOpenType(Date.class.getName(), new Date()), SimpleType.DATE);
        assertNull(TypeHelper.buildOpenType(java.sql.Date.class.getName(), new java.sql.Date(1L)));
        assertSame(TypeHelper.buildOpenType(ObjectName.class.getName(), new ObjectName("A:B=C")), SimpleType.OBJECTNAME);
    }

    @Test
    public void detectSimpleArrayTypes() throws Exception {
        // arrays of types other than CompositeData/TabularData don't need investigation
        assertEquals(TypeHelper.cache("x", "[Ljava.lang.String;", null).openType(), new ArrayType<>(1, SimpleType.STRING));
        assertEquals(TypeHelper.cache("x", "[[[Ljava.lang.String;", null).openType(), new ArrayType<>(3, SimpleType.STRING));

        ObjectName name = new ObjectName("a:b=1");

        // but still we should handle this without checking the value
        assertEquals(TypeHelper.buildOpenType("[[Ljava.lang.String;", null), new ArrayType<>(2, SimpleType.STRING));
        assertEquals(TypeHelper.buildOpenType("[[[[[Ljava.lang.String;", null), new ArrayType<>(5, SimpleType.STRING));
        assertEquals(TypeHelper.buildOpenType("[[[[[L" + ObjectName.class.getName() + ";", null), new ArrayType<>(5, SimpleType.OBJECTNAME));
    }

    @Test
    public void detectComplexArrayTypesWithoutDataToCheck() throws Exception {
        ObjectName name = new ObjectName("a:b=1");

        // Only for CompositeData/TabularData (or arrays of these) we need a value
        assertNull(TypeHelper.buildOpenType(CompositeData.class.getName(), null));
        assertNull(TypeHelper.buildOpenType("[[[[[L" + CompositeData.class.getName() + ";", null));
        assertNull(TypeHelper.buildOpenType(TabularData.class.getName(), null));
        assertNull(TypeHelper.buildOpenType("[[[[[L" + TabularData.class.getName() + ";", null));
    }

    @Test
    public void detectNonOpenArrayTypes() throws Exception {
        ObjectName name = new ObjectName("a:b=1");

        assertNull(TypeHelper.buildOpenType(File.class.getName(), null));
        assertNull(TypeHelper.buildOpenType("[[[[[L" + URL.class.getName() + ";", null));
    }

    @Test
    public void detectQuoteKnownQuoteCompositeType() throws Exception {
        MBeanServer jmx = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
        MBeanInfo info = jmx.getMBeanInfo(name);
        MBeanAttributeInfo usage = attrInfo(info, "HeapMemoryUsage");

        Object jsonValue = toJsonConverter.serialize(jmx.getAttribute(name, "HeapMemoryUsage"), null, SerializeOptions.DEFAULT);
        assertNotNull(TypeHelper.buildOpenType(usage.getType(), jsonValue));
    }

    @Test
    public void detectQuoteKnownQuoteTabularTypeInsideCompositeType() throws Exception {
        MBeanServer jmx = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = jmx.queryNames(new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=*"), null);
        if (names.isEmpty()) {
            throw new SkipException("Can't find GC MBeans");
        }
        MBeanInfo info = jmx.getMBeanInfo(names.iterator().next());
        OpenMBeanAttributeInfo usage = (OpenMBeanAttributeInfo) attrInfo(info, "LastGcInfo");

        System.gc();

        boolean found = false;
        for (ObjectName name : names) {
            Object lastGcInfo = jmx.getAttribute(name, "LastGcInfo");
            if (lastGcInfo == null) {
                continue;
            }
            found = true;
            Object jsonValue = toJsonConverter.serialize(lastGcInfo, new LinkedList<>(List.of("memoryUsageBeforeGc")), SerializeOptions.DEFAULT);
            CompositeType ct = (CompositeType) usage.getOpenType();
            assertNotNull(TypeHelper.buildOpenType(ct.getType("memoryUsageBeforeGc").getClassName(), jsonValue));
        }

        if (!found) {
            throw new SkipException("Can't find GC MBeans which return any data from LastGcInfo");
        }
    }

    private MBeanAttributeInfo attrInfo(MBeanInfo info, String name) {
        return Arrays.stream(info.getAttributes()).filter(attr -> attr.getName().equals(name)).findFirst().orElseThrow();
    }

}
