package org.jolokia.converter.object;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.config.ConfigKey;
import org.jolokia.util.DateUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;


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
 * @author roland
 * @since Feb 14, 2010
 */
public class StringToObjectConverterTest {

    StringToObjectConverter converter;

    @BeforeTest
    public void setup() {
       converter = new StringToObjectConverter();
    }

    @Test
    public void simpleConversions() {
        Object obj = converter.convertFromString(int.class.getCanonicalName(),"10");
        assertEquals("Int conversion",10,obj);
        obj = converter.convertFromString(Integer.class.getCanonicalName(),"10");
        assertEquals("Integer conversion",10,obj);
        obj = converter.convertFromString(Short.class.getCanonicalName(),"10");
        assertEquals("Short conversion",(short) 10,obj);
        obj = converter.convertFromString(short.class.getCanonicalName(),"10");
        assertEquals("short conversion",Short.parseShort("10"),obj);
        obj = converter.convertFromString(Long.class.getCanonicalName(),"10");
        assertEquals("long conversion",10L,obj);
        obj = converter.convertFromString(long.class.getCanonicalName(),"10");
        assertEquals("Long conversion",10L,obj);
        obj = converter.convertFromString(Byte.class.getCanonicalName(),"10");
        assertEquals("Byte conversion",(byte) 10,obj);
        obj = converter.convertFromString(byte.class.getCanonicalName(),"10");
        assertEquals("byte conversion",Byte.parseByte("10"),obj);

        obj = converter.convertFromString(Float.class.getCanonicalName(),"10.5");
        assertEquals("Float conversion",10.5f,obj);
        obj = converter.convertFromString(float.class.getCanonicalName(),"21.3");
        assertEquals("float conversion",new Float(21.3f),obj);
        obj = converter.convertFromString(Double.class.getCanonicalName(),"10.5");
        assertEquals("Double conversion",10.5d,obj);
        obj = converter.convertFromString(double.class.getCanonicalName(),"21.3");
        assertEquals("double conversion",21.3d,obj);
        obj = converter.convertFromString(BigDecimal.class.getCanonicalName(),"83.4e+4");
        assertEquals("BigDecimal conversion", new BigDecimal("8.34e+5"), obj);
        obj = converter.convertFromString(BigInteger.class.getCanonicalName(),"47110815471108154711");
        assertEquals("BigInteger conversion", new BigInteger("47110815471108154711"), obj);

        obj = converter.convertFromString(Boolean.class.getCanonicalName(),"false");
        assertEquals("Boolean conversion",false,obj);
        obj = converter.convertFromString(boolean.class.getCanonicalName(),"true");
        assertEquals("boolean conversion",true,obj);

        obj = converter.convertFromString(char.class.getCanonicalName(),"a");
        assertEquals("Char conversion",'a',obj);

        obj = converter.convertFromString("java.lang.String","10");
        assertEquals("String conversion","10",obj);
    }

    @Test
    public void jsonConversion() {
        JSONObject json = new JSONObject();
        json.put("name","roland");
        json.put("kind","jolokia");

        Object object = converter.convertFromString(JSONObject.class.getName(),json.toString());
        assertEquals(json,object);

        JSONArray array = new JSONArray();
        array.add("roland");
        array.add("jolokia");

        object = converter.convertFromString(JSONArray.class.getName(),array.toString());
        assertEquals(array,object);

        try {
            converter.convertFromString(JSONObject.class.getName(),"{bla:blub{");
            fail();
        } catch (IllegalArgumentException exp) {

        }
    }

    @Test
    public void urlConversion(){
     	URL url = null;
    	try {
    		url = new URL("http://google.com");
    	} catch (MalformedURLException e) {}     	
        Object object = converter.convertFromString(URL.class.getCanonicalName(),"http://google.com");
        assertEquals("URL conversion", url, object);
    }
    
    
    @Test
    public void enumConversion() {
        ConfigKey key = (ConfigKey) converter.prepareValue(ConfigKey.class.getName(), "MAX_DEPTH");
        assertEquals(key, ConfigKey.MAX_DEPTH);
    }



    @Test
    public void dateConversion() {
        Date date = (Date) converter.convertFromString(Date.class.getName(),"0");
        assertEquals(date.getTime(),0);
        Date now = new Date();
        date = (Date) converter.convertFromString(Date.class.getName(), DateUtil.toISO8601(now));
        assertEquals(date.getTime() / 1000,now.getTime() / 1000);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class})
    public void dateConversionFailed() {
        converter.prepareValue(Date.class.getName(),"illegal-date-format");
    }

    @Test
    public void objectNameConversion() throws MalformedObjectNameException {
    	String name = "JOLOKIA:class=Conversion,type=builder,name=jlk";
    	ObjectName objName = new ObjectName(name);
    	ObjectName testName = (ObjectName)converter.convertFromString(ObjectName.class.getName(), name);
    	assertEquals(objName, testName);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*parse.*ObjectName.*")
    public void objectNameConversionFailed() {
        converter.convertFromString(ObjectName.class.getName(),"bla:blub:InvalidName");
    }

    @Test
    public void arrayConversions() {
        Object obj = converter.convertFromString(new int[0].getClass().getName(),"10,20,30");
        int expected[] = new int[] { 10,20,30};
        for (int i = 0;i < expected.length;i++) {
            assertEquals(expected[i],((int[]) obj)[i]);
        }
        obj = converter.convertFromString(new Integer[0].getClass().getName(),"10,20,30");
        for (int i = 0;i < expected.length;i++) {
            assertEquals(expected[i],(int) ((Integer[]) obj)[i]);
        }

        // Escaped arrays
        String[] strings = (String[]) converter.convertFromString(new String[0].getClass().getName(),"hallo!,hans!!,wu!!rs!t");
        assertEquals(strings.length,2);
        assertEquals("hallo,hans!",strings[0]);
        assertEquals("wu!rst",strings[1]);

        try {
            obj = converter.convertFromString("[Lbla;","10,20,30");
            fail("Unknown object type");
        } catch (IllegalArgumentException exp) {}


        try {
            obj = converter.convertFromString("[X","10,20,30");
            fail("Unknown object type");
        } catch (IllegalArgumentException exp) {}
    }

    @Test
    public void checkNull() {
        Object obj = converter.convertFromString(new int[0].getClass().getName(),"[null]");
        assertNull("Null check",obj);
    }

    @Test
    public void checkEmptyString() {
        Object obj = converter.convertFromString("java.lang.String","\"\"");
        assertEquals("Empty String check",0,((String) obj).length());
        try {
            obj = converter.convertFromString("java.lang.Integer","\"\"");
            fail("Empty string conversion only for string");
        } catch (IllegalArgumentException exp) {}
    }

    @Test
    public void unknownExtractor() {
        try {
            Object obj = converter.convertFromString(this.getClass().getName(),"bla");
            fail("Unknown extractor");
        } catch (IllegalArgumentException exp) {};
    }

    @Test
    public void prepareValue() {
        assertNull(converter.prepareValue("java.lang.String", null));
        assertEquals(converter.prepareValue("java.lang.Long", 10L), 10L);
        assertEquals(converter.prepareValue("java.lang.Long", "10"), 10L);
        Map<String,String> map = new HashMap<String, String>();
        map.put("euro","fcn");
        assertTrue(converter.prepareValue("java.util.Map", map) == map);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void prepareValueInvalidClass() {
        converter.prepareValue("blubber.bla.hello",10L);
    }
    @Test
    public void prepareValueListConversion1() {
        List<Boolean> list = new ArrayList<Boolean>();
        list.add(true);
        list.add(false);
        boolean[] res = (boolean[]) converter.prepareValue("[Z",list);
        assertTrue(res[0]);
        assertFalse(res[1]);
        Assert.assertEquals(res.length,2);
    }

    @Test
    public void prepareValueListConversion2() {
        List<Boolean> list = new ArrayList<Boolean>();
        list.add(true);
        list.add(false);
        list.add(null);
        Boolean[] res = (Boolean[]) converter.prepareValue("[Ljava.lang.Boolean;",list);
        assertTrue(res[0]);
        assertFalse(res[1]);
        assertNull(res[2]);
        Assert.assertEquals(res.length,3);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void prepareValueWithException() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(10);
        list.add(null);
        converter.prepareValue("[I",list);
    }
    
    public static class Example {
    	private String value;
    	private List<String> list;
    	
    	public Example(String value) { this.value = value; }
    	public Example(List<String> list) { this.list = list; }
    	
    	public String getValue() { return value; }
    	public List<String> getList() { return list; }
    }
    
    public static class PrivateExample {
    	private String value;
    	private PrivateExample(String value) { this.value = value; }
    	public String getValue() { return value; }
    }
    
    public static class MultipleConstructorExample {
    	private String value;
    	private List<String> list;
    	
    	public MultipleConstructorExample(String value, List<String> list) { 
    		this.value = value;
    		this.list = list;
    	}
    	
    	public String getValue() { return value; }
    	public List<String> getList() { return list; }
    }
    
    @Test
    public void prepareValueWithConstructor() {
    	Object o = converter.prepareValue(this.getClass().getCanonicalName() + "$Example", "test");
    	assertTrue(o instanceof Example);
    	assertEquals("test", ((Example)o).getValue());
    }
    
    @Test
    public void prepareValueWithConstructorList() {
    	Object o = converter.prepareValue(this.getClass().getCanonicalName() + "$Example", Arrays.asList("test"));
    	assertTrue(o instanceof Example);
    	assertNull(((Example)o).getList());
    	assertEquals("[test]", ((Example)o).getValue());
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class, 
    	  expectedExceptionsMessageRegExp = "Cannot convert string test to type "
      	  		+ "org.jolokia.converter.object.StringToObjectConverterTest\\$PrivateExample "
      	  		+ "because no converter could be found")
    public void prepareValueWithPrivateExample() {
    	converter.prepareValue(this.getClass().getCanonicalName() + "$PrivateExample", "test");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
    	  expectedExceptionsMessageRegExp = "Cannot convert string test to type "
    	  		+ "org.jolokia.converter.object.StringToObjectConverterTest\\$MultipleConstructorExample "
    	  		+ "because no converter could be found")
    public void prepareValueWithMultipleConstructors() {
    	converter.prepareValue(this.getClass().getCanonicalName() + "$MultipleConstructorExample", "test");
    }
    
    @Test
    public void dateConversionNotByConstructor() throws ParseException {
    	final String dateStr = "2015-11-20T00:00:00+00:00";
    	
    	try {
    		new Date(dateStr);
    		fail("Should have throw IllegalArgumentException");
    	} catch (IllegalArgumentException ignore) {}
    	
    	// new Date(dateStr) will throw IllegalArgumentException but our convert does not. 
    	// so it does not use Constructor to convert date
    	Object obj = converter.convertFromString(Date.class.getCanonicalName(), dateStr);
    	assertNotNull(obj);
    	assertTrue(obj instanceof Date);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    	Date expectedDate = sdf.parse(dateStr.replaceFirst("\\+(0\\d)\\:(\\d{2})$", "+$1$2"));
    	assertEquals(expectedDate, obj);
    }
}
