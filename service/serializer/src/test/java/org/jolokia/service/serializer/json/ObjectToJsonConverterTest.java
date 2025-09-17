package org.jolokia.service.serializer.json;

import java.beans.Transient;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;

import org.jolokia.json.JSONObject;
import org.jolokia.service.serializer.object.ObjectToObjectConverter;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.testng.annotations.*;

import static org.testng.AssertJUnit.*;


/*
 * Copyright 2009-2013 Roland Huss
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

/**
 * Testing the converter
 *
 * @author roland
 * @since Jul 24, 2009
 */
public class ObjectToJsonConverterTest {

    private ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        converter = new ObjectToJsonConverter(new ObjectToObjectConverter(), null);
        converter.setupContext();
    }

    @AfterMethod
    public void tearDown() {
        converter.clearContext();
    }

    @Test
    public void basics() throws AttributeNotFoundException {
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new SelfRefBean1(), new LinkedList<>(), true);
        assertNotNull("Bean2 is set",result.get("bean2"));
        assertNotNull("Binary attribute is set",result.get("strong"));
    }

    @Test
    public void checkDeadLockDetection() throws AttributeNotFoundException {
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new SelfRefBean1(), new LinkedList<>(), true);
        assertNotNull("Bean 2 is set",result.get("bean2"));
        //noinspection unchecked
        assertNotNull("Bean2:Bean1 is set",((Map<String, ?>)result.get("bean2")).get("bean1"));
        //noinspection unchecked
        assertEquals("Reference breackage", String.class, ((Map<String, ?>)result.get("bean2")).get("bean1").getClass());
        assertTrue("Bean 3 should be resolved",result.get("bean3") instanceof Map);
    }

    @Test
    public void maxDepth() throws AttributeNotFoundException, NoSuchFieldException, IllegalAccessException {
        setOptionsViaReflection("maxDepth",2);
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new SelfRefBean1(), new LinkedList<>(), true);
        @SuppressWarnings("unchecked")
        String c = (String) ((Map<String, ?>) result.get("bean2")).get("bean1");
        assertTrue("Recurence detected",c.contains("bean1: toString"));
    }

    @Test
    public void maxObjects() throws NoSuchFieldException, IllegalAccessException, AttributeNotFoundException {
        setOptionsViaReflection("maxObjects",1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) converter.extractObject(new InnerValueTestBean("foo", "bar", "baz"), new LinkedList<>(), true);
        boolean found = false;
        for (Object val : result.values()) {
            if (val instanceof String) {
                found = ((String) val).matches("^\\[.*(limit).*]$");
            }
        }
        assertTrue(found);
    }

    private void setOptionsViaReflection(String pLimit, int pVal) throws NoSuchFieldException, IllegalAccessException {
        ObjectSerializationContext ctx = converter.getStackContextLocal().get();
        Field field = ObjectSerializationContext.class.getDeclaredField("options");
        field.setAccessible(true);
        SerializeOptions opts = (SerializeOptions) field.get(ctx);
        field = SerializeOptions.class.getDeclaredField(pLimit);
        field.setAccessible(true);
        field.set(opts,pVal);
    }

    @Test
    public void customSimplifier() throws AttributeNotFoundException {
        Date date = new Date();
        JSONObject result = (JSONObject) converter.extractObject(date, new LinkedList<>(), true);
        assertNotNull(result);
        assertEquals(date.getTime(), result.get("millis"));
    }

    @Test
    public void fileSimplifier() throws AttributeNotFoundException {
        JSONObject result = (JSONObject) converter.extractObject(new File("/tmp"), new LinkedList<>(), true);
        assertNotNull(result);
        assertNull(result.get("parent"));
    }

    @Test
    public void customNegativeSimplifier() throws MalformedObjectNameException, AttributeNotFoundException {
        ObjectName name = new ObjectName("java.lang:type=Memory");
        JSONObject result = (JSONObject) converter.extractObject(name, new LinkedList<>(), true);
        // Since we removed the objectName simplifier from the list of simplifiers-default
        // explicitly, the converter should return the full-blown object;
        assertNotNull(result);
        assertEquals("type=Memory",result.get("canonicalKeyPropertyListString"));
    }

    @Test
    public void convertToJsonTest() throws AttributeNotFoundException {
        File file = new File("myFile");

        @SuppressWarnings("unchecked")
        Map<String, ?> ret = (Map<String, ?>) converter.serialize(file, null, SerializeOptions.DEFAULT);
        assertEquals("myFile", ret.get("name"));
        String name = (String) converter.serialize(file, List.of("name"), SerializeOptions.DEFAULT);
        assertEquals("myFile", name);
    }

    @Test
    public void setInnerValueTest() throws IllegalAccessException, AttributeNotFoundException, InvocationTargetException {
        InnerValueTestBean bean = new InnerValueTestBean("foo", "bar", "baz");

        Object oldValue = converter.setInnerValue(bean, "blub", new ArrayList<>(Arrays.asList("map", "foo", "1")));

        assertEquals("baz", oldValue);
        assertEquals("bar", bean.getMap().get("foo").get(0));
        assertEquals("blub", bean.getMap().get("foo").get(1));

        oldValue = converter.setInnerValue(bean, "fcn", new ArrayList<>(Arrays.asList("array", "0")));

        assertEquals("bar", oldValue);
        assertEquals("fcn", bean.getArray()[0]);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void setInnerValueTestWithoutPathParts() throws IllegalAccessException, AttributeNotFoundException, InvocationTargetException {
        InnerValueTestBean bean = new InnerValueTestBean("foo", "bar", "baz");

        converter.setInnerValue(bean, "blub", new ArrayList<>());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void setInnerValueTestWithIncompatiblePathPart() throws IllegalAccessException, AttributeNotFoundException, InvocationTargetException {
        InnerValueTestBean bean = new InnerValueTestBean("foo", "bar", "baz");

        converter.setInnerValue(bean, "blub", new ArrayList<>(Collections.singletonList("map")));
    }

    @Test
    public void convertTransientValue() throws AttributeNotFoundException {
        TransientValueBean bean = new TransientValueBean();
        bean.value = "value";
        bean.transientValue = "transient";

        String version = System.getProperty("java.specification.version");
        if (version.contains(".")) {
            version = version.substring(version.lastIndexOf('.') + 1);
        }
        int v = Integer.parseInt(version);
        if (v > 6) {
            @SuppressWarnings("unchecked")
            Map<String, ?> ret =  (Map<String, ?>)converter.serialize(bean, null, SerializeOptions.DEFAULT);
            assertNull(ret.get("transientValue"));
            assertEquals("value", ret.get("value"));
        } else {
            try {
                converter.serialize(bean, null, SerializeOptions.DEFAULT);
                fail("Should throw SecurityException(\"Prohibited package name: java.beans\")");
            } catch (SecurityException e) {
                assertEquals("Prohibited package name: java.beans", e.getMessage());
            }
        }
    }

    @Test
    public void convertLong() throws AttributeNotFoundException {
        long value = 900719925474099123L;
        Object ret1 = converter.serialize(value, null, SerializeOptions.DEFAULT);
        assertEquals(900719925474099123L, ret1);
        SerializeOptions.Builder builder = new SerializeOptions.Builder();
        Object ret2 = converter.serialize(value, null, builder.serializeLong("string").build());
        assertEquals("900719925474099123", ret2);
    }

    // ============================================================================
    // TestBeans:

    class SelfRefBean1 {

        SelfRefBean2 bean2;
        SelfRefBean3 bean3;

        boolean strong;

        SelfRefBean1() {
            bean3 = new SelfRefBean3(this);
            bean2 = new SelfRefBean2(this,bean3);
        }

        public SelfRefBean2 getBean2() {
            return bean2;
        }

        public SelfRefBean3 getBean3() {
            return bean3;
        }

        public boolean isStrong() {
            return strong;
        }

        public String toString() {
            return "bean1: toString";
        }
    }

    class SelfRefBean2 {

        SelfRefBean1 bean1;
        SelfRefBean3 bean3;

        SelfRefBean2(SelfRefBean1 pBean1,SelfRefBean3 pBean3) {
            bean1 = pBean1;
            bean3 = pBean3;
        }

        public SelfRefBean1 getBean1() {
            return bean1;
        }

        public SelfRefBean3 getBean3() {
            return bean3;
        }
    }

    class SelfRefBean3 {

        SelfRefBean1 bean1;

        SelfRefBean3(SelfRefBean1 pBean1) {
            bean1 = pBean1;
        }

        public SelfRefBean1 getBean1() {
            return bean1;
        }
    }

    static class InnerValueTestBean {
        private Map<String, List<String>> map;

        private final String[] array;

        InnerValueTestBean(String key, String value1, String value2) {
            map = new HashMap<>();
            map.put(key,Arrays.asList(value1, value2));

            array = new String[] { value1, value2 };
        }

        public Map<String, List<String>> getMap() {
            return map;
        }

        public void setMap(Map<String, List<String>> map) {
            this.map = map;
        }

        public String[] getArray() {
            return array;
        }
    }

    static class TransientValueBean {

        String value;

        transient String transientValue;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Transient
        public String getTransientValue() {
            return transientValue;
        }

        @Transient
        public void setTransientValue(String transientValue) {
            this.transientValue = transientValue;
        }
    }

}
