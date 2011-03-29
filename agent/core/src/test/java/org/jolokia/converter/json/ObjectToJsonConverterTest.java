package org.jolokia.converter.json;

import org.jolokia.request.*;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import javax.management.*;

import java.io.File;
import java.util.*;

import static org.testng.AssertJUnit.*;


/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
        converter = new ObjectToJsonConverter(new StringToObjectConverter(),null);
        converter.setupContext(null,null,null,null);
    }

    @AfterMethod
    public void tearDown() {
        if (converter != null) {
            converter.clearContext();
        }
    }

    @Test
    public void basics() throws AttributeNotFoundException {
        Map result = (Map) converter.extractObject(new SelfRefBean1(),new Stack<String>(),true);
        assertNotNull("Bean2 is set",result.get("bean2"));
        assertNotNull("Binary attribute is set",result.get("strong"));
    }

    @Test
    public void checkDeadLockDetection() throws AttributeNotFoundException {
        Map result = (Map) converter.extractObject(new SelfRefBean1(),new Stack<String>(),true);
        assertNotNull("Bean 2 is set",result.get("bean2"));
        assertNotNull("Bean2:Bean1 is set",((Map)result.get("bean2")).get("bean1"));
        assertEquals("Reference breackage",((Map)result.get("bean2")).get("bean1").getClass(),String.class);
        assertTrue("Bean 3 should be resolved",result.get("bean3") instanceof Map);
    }

    @Test
    public void maxDepth() throws AttributeNotFoundException {
        ObjectToJsonConverter.StackContext ctx = converter.getStackContextLocal().get();
        ctx.setMaxDepth(1);
        Map result = (Map) converter.extractObject(new SelfRefBean1(),new Stack<String>(),true);
        String c = (String) ((Map) result.get("bean2")).get("bean1");
        assertTrue("Recurence detected",c.contains("bean1: toString"));
    }

    @Test
    public void customSimplifier() throws AttributeNotFoundException {
        Date date = new Date();
        Map result = (Map) converter.extractObject(date,new Stack<String>(),true);
        assertEquals(date.getTime(),result.get("millis"));
    }

    @Test
    public void fileSimplifier() throws AttributeNotFoundException {
        Map result = (Map) converter.extractObject(new File("/tmp"),new Stack<String>(),true);
        assertNull(result.get("parent"));
    }

    @Test
    public void customNegativeSimpifier() throws MalformedObjectNameException, AttributeNotFoundException {
        ObjectName name = new ObjectName("java.lang:type=Memory");
        Map result = (Map) converter.extractObject(name,new Stack<String>(),true);
        // Since we removed the objectname simplifier from the list of simplifiers
        // explicitely, the converter should return the full blown object;
        assertEquals("type=Memory",result.get("canonicalKeyPropertyListString"));
    }

    @Test
    public void convertToJsonTest() throws MalformedObjectNameException, AttributeNotFoundException {
        JmxRequest req =
                new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory").
                        pathParts("name").build();
        File file = new File("myFile");
        JSONObject ret = converter.convertToJson(file,req,false);
        assertEquals( ((Map) ret.get("value")).get("name"),"myFile");
        ret = converter.convertToJson(file,req,true);
        assertEquals(ret.get("value"),"myFile");
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
}

