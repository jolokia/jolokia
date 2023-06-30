package org.jolokia.converter.json;

import java.beans.Transient;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;

import org.jolokia.converter.object.StringToObjectConverter;
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
        Map result = (Map) converter.extractObject(new SelfRefBean1(), new Stack<String>(), true);
        assertNotNull("Bean2 is set",result.get("bean2"));
        assertNotNull("Binary attribute is set",result.get("strong"));
    }

    @Test
    public void checkDeadLockDetection() throws AttributeNotFoundException {
        Map result = (Map) converter.extractObject(new SelfRefBean1(), new Stack<String>(), true);
        assertNotNull("Bean 2 is set",result.get("bean2"));
        assertNotNull("Bean2:Bean1 is set",((Map)result.get("bean2")).get("bean1"));
        assertEquals("Reference breackage",((Map)result.get("bean2")).get("bean1").getClass(),String.class);
        assertTrue("Bean 3 should be resolved",result.get("bean3") instanceof Map);
    }

    @Test
    public void maxDepth() throws AttributeNotFoundException, NoSuchFieldException, IllegalAccessException {
        setOptionsViaReflection("maxDepth",2);
        Map result = (Map) converter.extractObject(new SelfRefBean1(), new Stack<String>(), true);
        String c = (String) ((Map) result.get("bean2")).get("bean1");
        assertTrue("Recurence detected",c.contains("bean1: toString"));
    }

    @Test
    public void maxObjects() throws NoSuchFieldException, IllegalAccessException, AttributeNotFoundException {
        setOptionsViaReflection("maxObjects",1);
        Map<String,Object> result = (Map) converter.extractObject(new InnerValueTestBean("foo", "bar", "baz"), new Stack<String>(), true);
        boolean found = false;
        for (Object val : result.values()) {
            if (val instanceof String) {
                found = ((String) val).matches("^\\[.*(limit).*\\]$");
            }
        }
        assertTrue(found);
    }

    private void setOptionsViaReflection(String pLimit, int pVal) throws NoSuchFieldException, IllegalAccessException {
        ObjectSerializationContext ctx = converter.getStackContextLocal().get();
        Field field = ObjectSerializationContext.class.getDeclaredField("options");
        field.setAccessible(true);
        JsonConvertOptions opts = (JsonConvertOptions) field.get(ctx);
        field = JsonConvertOptions.class.getDeclaredField(pLimit);
        field.setAccessible(true);
        field.set(opts,pVal);
    }


    @Test
    public void customSimplifier() throws AttributeNotFoundException {
        Date date = new Date();
        Map result = (Map) converter.extractObject(date, new Stack<String>(), true);
        assertEquals(date.getTime(),result.get("millis"));
    }

    @Test
    public void fileSimplifier() throws AttributeNotFoundException {
        Map result = (Map) converter.extractObject(new File("/tmp"), new Stack<String>(), true);
        assertNull(result.get("parent"));
    }

    @Test
    public void customNegativeSimpifier() throws MalformedObjectNameException, AttributeNotFoundException {
        ObjectName name = new ObjectName("java.lang:type=Memory");
        Map result = (Map) converter.extractObject(name, new Stack<String>(), true);
        // Since we removed the objectname simplifier from the list of simplifiers
        // explicitely, the converter should return the full blown object;
        assertEquals("type=Memory",result.get("canonicalKeyPropertyListString"));
    }

    @Test
    public void convertToJsonTest() throws MalformedObjectNameException, AttributeNotFoundException {
        File file = new File("myFile");

        Map ret = (Map) converter.convertToJson(file, null, JsonConvertOptions.DEFAULT);
        assertEquals(ret.get("name"),"myFile");
        String name = (String) converter.convertToJson(file, Arrays.asList("name"), JsonConvertOptions.DEFAULT);
        assertEquals(name,"myFile");
    }

    @Test
    public void setInnerValueTest() throws IllegalAccessException, AttributeNotFoundException, InvocationTargetException {
        InnerValueTestBean bean = new InnerValueTestBean("foo","bar","baz");

        Object oldValue = converter.setInnerValue(bean,"blub",new ArrayList<String>(Arrays.asList("map","foo","1")));

        assertEquals(oldValue,"baz");
        assertEquals(bean.getMap().get("foo").get(0),"bar");
        assertEquals(bean.getMap().get("foo").get(1),"blub");

        oldValue = converter.setInnerValue(bean,"fcn",new ArrayList<String>(Arrays.asList("array","0")));

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
            Map ret =  (Map)converter.convertToJson(bean, null, JsonConvertOptions.DEFAULT);
            assertNull(ret.get("transientValue"));
            assertEquals(ret.get("value"),"value");
        } else {
            try {
                converter.convertToJson(bean, null, JsonConvertOptions.DEFAULT);
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

    class InnerValueTestBean {
        private Map<String, List<String>> map;

        private String[] array;

        InnerValueTestBean(String key, String value1, String value2) {
            map = new HashMap<String, List<String>>();
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

    class TransientValueBean {

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