package org.jolokia.service.serializer.json;

import java.beans.Transient;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;

import org.jolokia.service.serializer.object.StringToObjectConverter;
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
        converter = new ObjectToJsonConverter(new StringToObjectConverter());
        converter.setupContext();
    }

    @AfterMethod
    public void tearDown() {
        converter.clearContext();
    }

    @Test
    public void basics() throws AttributeNotFoundException {
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new SelfRefBean1(), new Stack<>(), true);
        assertNotNull("Bean2 is set",result.get("bean2"));
        assertNotNull("Binary attribute is set",result.get("strong"));
    }

    @Test
    public void checkDeadLockDetection() throws AttributeNotFoundException {
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new SelfRefBean1(), new Stack<>(), true);
        assertNotNull("Bean 2 is set",result.get("bean2"));
        //noinspection unchecked
        assertNotNull("Bean2:Bean1 is set",((Map<String, ?>)result.get("bean2")).get("bean1"));
        //noinspection unchecked
        assertEquals("Reference breackage",((Map<String, ?>)result.get("bean2")).get("bean1").getClass(),String.class);
        assertTrue("Bean 3 should be resolved",result.get("bean3") instanceof Map);
    }

    @Test
    public void maxDepth() throws AttributeNotFoundException, NoSuchFieldException, IllegalAccessException {
        setOptionsViaReflection("maxDepth",2);
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new SelfRefBean1(), new Stack<>(), true);
        @SuppressWarnings("unchecked")
        String c = (String) ((Map<String, ?>) result.get("bean2")).get("bean1");
        assertTrue("Recurence detected",c.contains("bean1: toString"));
    }

    @Test
    public void maxObjects() throws NoSuchFieldException, IllegalAccessException, AttributeNotFoundException {
        setOptionsViaReflection("maxObjects",1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) converter.extractObject(new InnerValueTestBean("foo", "bar", "baz"), new Stack<>(), true);
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
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(date, new Stack<>(), true);
        assertEquals(date.getTime(),result.get("millis"));
    }

    @Test
    public void fileSimplifier() throws AttributeNotFoundException {
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(new File("/tmp"), new Stack<>(), true);
        assertNull(result.get("parent"));
    }

    @Test
    public void customNegativeSimpifier() throws MalformedObjectNameException, AttributeNotFoundException {
        ObjectName name = new ObjectName("java.lang:type=Memory");
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) converter.extractObject(name, new Stack<>(), true);
        // Since we removed the objectname simplifier from the list of simplifiers-default
        // explicitely, the converter should return the full-blown object;
        assertEquals("type=Memory",result.get("canonicalKeyPropertyListString"));
    }

    @Test
    public void convertToJsonTest() throws AttributeNotFoundException {
        File file = new File("myFile");

        @SuppressWarnings("unchecked")
        Map<String, ?> ret = (Map<String, ?>) converter.serialize(file, null, SerializeOptions.DEFAULT);
        assertEquals(ret.get("name"),"myFile");
        String name = (String) converter.serialize(file, List.of("name"), SerializeOptions.DEFAULT);
        assertEquals(name,"myFile");
    }

    @Test
    public void setInnerValueTest() throws IllegalAccessException, AttributeNotFoundException, InvocationTargetException {
        InnerValueTestBean bean = new InnerValueTestBean("foo", "bar", "baz");

        Object oldValue = converter.setInnerValue(bean, "blub", new ArrayList<>(Arrays.asList("map", "foo", "1")));

        assertEquals(oldValue,"baz");
        assertEquals(bean.getMap().get("foo").get(0),"bar");
        assertEquals(bean.getMap().get("foo").get(1),"blub");

        oldValue = converter.setInnerValue(bean, "fcn", new ArrayList<>(Arrays.asList("array", "0")));

        assertEquals(oldValue,"bar");
        assertEquals(bean.getArray()[0],"fcn");
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
            assertEquals(ret.get("value"),"value");
        } else {
            try {
                converter.serialize(bean, null, SerializeOptions.DEFAULT);
                fail("Should throw SecurityException(\"Prohibited package name: java.beans\")");
            } catch (SecurityException e) {
                assertEquals("Prohibited package name: java.beans", e.getMessage());
            }
        }
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
        private final Map<String, List<String>> map;

        private final String[] array;

        InnerValueTestBean(String key, String value1, String value2) {
            map = new HashMap<>();
            map.put(key,Arrays.asList(value1, value2));

            array = new String[] { value1, value2 };
        }

        public Map<String, List<String>> getMap() {
            return map;
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
