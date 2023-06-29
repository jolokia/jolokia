package org.jolokia.converter.json;

/*
 * Copyright 2009-2011 Roland Huss
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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;
import javax.management.openmbean.*;

import org.jolokia.converter.object.StringToObjectConverter;
import org.jolokia.converter.util.CompositeTypeAndJson;
import org.jolokia.converter.util.TabularTypeAndJson;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static javax.management.openmbean.SimpleType.*;
import static org.testng.Assert.*;
/**
 * @author roland
 * @since 05.08.11
 */
@Test
public class TabularDataExtractorTest {

    private static final String TEST_VALUE = "value1";

    TabularDataExtractor extractor = new TabularDataExtractor();

    ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        converter = new ObjectToJsonConverter(new StringToObjectConverter());
        converter.setupContext(new JsonConvertOptions.Builder().useAttributeFilter(true).build());
    }

    @AfterMethod
    public void tearDown() {
        converter.clearContext();
    }

    @Test
    public void typeInfo() {
        assertEquals(extractor.getType(), TabularData.class);
        assertFalse(extractor.canSetValue());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*written to.*")
    public void setValue() throws InvocationTargetException, IllegalAccessException {
        extractor.setObjectValue(new StringToObjectConverter(), new Object(), "bla", "blub");
    }

    @Test
    public void extractMapAsJson() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING, "key1", TEST_VALUE);
        JSONObject result = (JSONObject) extract(true, data);
        assertNull(result.get("key2"));
        assertEquals(result.get("key1"), TEST_VALUE);
        assertEquals(result.size(), 1);
    }

    @Test
    public void extractMapAsJsonWithPath() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING, "key1", TEST_VALUE);
        assertEquals(extract(true, data, "key1"),TEST_VALUE);
    }

    @Test
    public void extractMapAsJsonWithWildcardPath() throws OpenDataException, AttributeNotFoundException, MalformedObjectNameException {
        TabularData data = prepareMxMBeanMapData("key1", "test:type=bla", "key2", "java.lang:type=Memory");

        JSONObject result = (JSONObject) extract(true, data, null, "domain");
        assertEquals(result.size(), 2);
        assertEquals(result.get("key1"),"test");
        assertEquals(result.get("key2"),"java.lang");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void extractMapAsJsonWithWildcardPathButFiltered() throws OpenDataException, AttributeNotFoundException, MalformedObjectNameException {
        TabularData data = prepareMxMBeanMapData("key1", "test:type=bla", "key2", "java.lang:type=Memory");
        extract(true, data, null, "noKnownAttribute");
    }


    private TabularData prepareMxMBeanMapData(String ... keyAndValues) throws OpenDataException,
                                                                        MalformedObjectNameException {
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
                STRING, "key", null,
                OBJECTNAME, "value", null
        );

        TabularTypeAndJson taj = new TabularTypeAndJson(new String[]{"key"}, ctj);
        TabularData data = new TabularDataSupport(taj.getType());

        for (int i = 0; i < keyAndValues.length; i+=2) {
            CompositeData cd = new CompositeDataSupport(ctj.getType(), new String[]{"key", "value"},
                                                        new Object[]{keyAndValues[i],
                                                                     new ObjectName(keyAndValues[i+1])});
            data.put(cd);
        }
        return data;
    }


    @Test
    public void extractMapWithComplexType() throws OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson cdj = new CompositeTypeAndJson(STRING, "name", "roland", INTEGER, "date", 1968);
        TabularData data = getMapTabularData(cdj.getType(),cdj.getCompositeData(),TEST_VALUE);

        JSONObject result = (JSONObject) extract(true,data);
        assertEquals(result.size(), 2);
        assertTrue(result.containsKey("indexNames"));
        assertTrue(result.containsKey("values"));
        List indexNames = (List) result.get("indexNames");
        assertEquals(indexNames.size(), 1);
        assertTrue(indexNames.contains("key"));
        List values = (List) result.get("values");
        assertEquals(values.size(),1);
        JSONObject value = (JSONObject) values.get(0);
        JSONObject key = (JSONObject) value.get("key");
        assertEquals(key.get("name"),"roland");
        assertEquals(key.get("date"),1968);
        assertEquals(key.size(), 2);

        assertEquals(value.get("value"), TEST_VALUE);
        assertEquals(key.size(),2);
    }

    @Test
    void extractMapDirect() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING,"key1",TEST_VALUE);
        TabularData data2 = (TabularData) extract(false,data);
        assertEquals(data2,data);
    }

    @Test
    void extractMapWithPath() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING,"key1",TEST_VALUE);
        Object result = extract(true,data,"key1");
        assertEquals(result,TEST_VALUE);
    }

    @Test
    void extractGenericTabularDataWithJsonEmpty() throws OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
                STRING,"name",null,
                STRING,"firstname",null,
                INTEGER,"age",null,
                BOOLEAN,"male",null
        );
        TabularTypeAndJson taj = new TabularTypeAndJson(new String[] { "name", "firstname" },ctj);
        TabularData data = new TabularDataSupport(taj.getType());

        JSONObject result = (JSONObject) extract(true, data);

        assertTrue(result.isEmpty());
    }

    @Test
    void extractGenericTabularDataWithJson() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getComplexTabularData();
        JSONObject result = (JSONObject) extract(true, data);
        assertEquals(result.size(),2);
        assertTrue(result.containsKey("meyer"));
        assertTrue(result.containsKey("huber"));
        JSONObject meyerMap = (JSONObject) result.get("meyer");
        assertTrue(meyerMap.containsKey("xaver"));
        assertTrue(meyerMap.containsKey("zensi"));
        assertEquals(meyerMap.size(),2);
        JSONObject zensiMap = (JSONObject) meyerMap.get("zensi");
        assertEquals(zensiMap.get("name"),"meyer");
        assertEquals(zensiMap.get("firstname"),"zensi");
        assertEquals(zensiMap.get("age"),28);
        assertEquals(zensiMap.get("male"),false);
        assertEquals(zensiMap.size(), 4);
    }

    @Test
    void extractGenericTabularData() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getComplexTabularData();
        TabularData data2 = (TabularData) extract(false, data);
        assertEquals(data2,data);

    }

    @Test
    void extractGenericTabularDataWithPath() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getComplexTabularData();
        JSONObject result = (JSONObject) extract(true,data,"meyer","xaver");
        assertEquals(result.size(),4);
        assertEquals(result.get("age"),12);
        assertEquals(result.get("male"),true);
    }

    @Test
    void extractGenericTabularDataWithIntegerAndObjectNamePath() throws OpenDataException, AttributeNotFoundException, MalformedObjectNameException {
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[] { "bundleId", "oName" },
                new CompositeTypeAndJson(
                        LONG,"bundleId",null,
                        OBJECTNAME,"oName",null,
                        BOOLEAN,"active",null
                ));
        TabularData data = new TabularDataSupport(taj.getType());
        data.put(new CompositeDataSupport(
                taj.getType().getRowType(),
                new String[]{"bundleId", "oName", "active"},
                new Object[]{10L,new ObjectName("test:type=bundle"), false}
        ));
        JSONObject result = (JSONObject) extract(true, data, "10", "test:type=bundle");
        assertEquals(result.size(),3);
        assertEquals(result.get("bundleId"),10L);
        assertEquals(result.get("active"),false);
    }

    @Test
    public void extractWithWildCardPath() throws OpenDataException, MalformedObjectNameException, AttributeNotFoundException {
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[] { "id"},
                new CompositeTypeAndJson(
                        LONG,"id",null,
                        OBJECTNAME,"oName",null,
                        BOOLEAN,"flag",null
                ));

        TabularData data = new TabularDataSupport(taj.getType());
        data.put(new CompositeDataSupport(
                taj.getType().getRowType(),
                new String[]{"id","oName","flag" },
                new Object[]{10L,new ObjectName("test:type=bundle"), false}
        ));

        data.put(new CompositeDataSupport(
                taj.getType().getRowType(),
                new String[]{"id","oName","flag" },
                new Object[]{20L,new ObjectName("java.lang:type=Memory"), true}
        ));

        // Path: */*/domain --> 1. Level: 10, 20 -- 2. Level: CD key-value (e.g {id: 10, oName: test=type...}), -- 3. level: Objects
        // Here: Only ObjetNames should be picked
        JSONObject result = (JSONObject) extract(true, data, null, null,"domain");
        assertEquals(result.size(),2); // 10 & 20
        JSONObject inner = (JSONObject) result.get(10L);
        assertEquals(inner.size(),1);
        assertEquals(inner.get("oName"),"test");
        inner = (JSONObject) result.get(20L);
        assertEquals(inner.size(),1);
        assertEquals(inner.get("oName"),"java.lang");

        // Path: */oName --> 1. Level: 10,20 -- 2. Level: { oName : { 10 vals}}
        result = (JSONObject) extract(true, data, null, "oName");
        assertEquals(result.size(),2);
        inner = (JSONObject) result.get(10L);
        assertEquals(inner.get("domain"),"test");
        inner = (JSONObject) result.get(20L);
        assertEquals(inner.get("domain"),"java.lang");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void noMatchWithWildcardPattern() throws OpenDataException, MalformedObjectNameException, AttributeNotFoundException {
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[] { "oName"},
                new CompositeTypeAndJson(
                        OBJECTNAME,"oName",null
                ));

        TabularData data = new TabularDataSupport(taj.getType());
        data.put(new CompositeDataSupport(
                taj.getType().getRowType(),
                new String[]{"oName" },
                new Object[]{new ObjectName("test:type=bundle")}
        ));

        extract(true, data, null, "oName2");
    }


    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    void extractGenericTabularDataWithToShortPath() throws OpenDataException, AttributeNotFoundException {
        extract(true, getComplexTabularData(), "meyer");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Boolean.*")
    void extractTabularDataWithPathButWrongIndexType() throws OpenDataException, AttributeNotFoundException {
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[] { "verein", "absteiger" },
                new CompositeTypeAndJson(
                        STRING,"verein",null,
                        INTEGER,"platz",null,
                        BOOLEAN,"absteiger",null
                ));
        TabularData data = new TabularDataSupport(taj.getType());
        data.put(new CompositeDataSupport(
                taj.getType().getRowType(),
                new String[] { "verein", "platz", "absteiger" },
                new Object[] { "fcn", 6, false }
        ));
        extract(true,data,"fcn","true");
    }


    @Test
    void emptyMxMBeanTabularData() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        TabularData data = prepareMxMBeanMapData();
        JSONObject result = (JSONObject) extract(true, data);
        assertNotNull(result);
        assertEquals(result.size(),0);
    }

    @Test
    void emptyTabularData() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        CompositeType type = new CompositeType(
                "testType",
                "Type for testing",
                new String[] { "testKey" },
                new String[] { "bla" },
                new OpenType[] { STRING }
                );
        TabularData data = new TabularDataSupport(new TabularType("test","test desc",type,new String[] { "testKey"}));
        JSONObject result = (JSONObject) extract(true, data);
        assertNotNull(result);
        assertEquals(result.size(),0);
    }



    private TabularData getComplexTabularData() throws OpenDataException {
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
                STRING,"name",null,
                STRING,"firstname",null,
                INTEGER,"age",null,
                BOOLEAN,"male",null
        );
        TabularTypeAndJson taj = new TabularTypeAndJson(new String[] { "name", "firstname" },ctj);
        TabularData data = new TabularDataSupport(taj.getType());
        data.put(new CompositeDataSupport(ctj.getType(),
                                          new String[] { "name", "firstname", "age", "male" },
                                          new Object[] { "meyer", "xaver", 12, true }));
        data.put(new CompositeDataSupport(ctj.getType(),
                                          new String[] { "name", "firstname", "age", "male" },
                                          new Object[] { "meyer", "zensi", 28, false }));
        data.put(new CompositeDataSupport(ctj.getType(),
                                          new String[] { "name", "firstname", "age", "male" },
                                          new Object[] { "huber", "erna", 18, false }));
        return data;
    }


    private TabularData getMapTabularData(OpenType keyType, Object ... keyAndValues) throws OpenDataException {
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
                keyType,"key",null,
                STRING,"value",null
        );

        TabularTypeAndJson taj = new TabularTypeAndJson(new String[] { "key" },ctj);
        TabularData data = new TabularDataSupport(taj.getType());

        for (int i = 0; i < keyAndValues.length; i+=2) {
            CompositeData cd = new CompositeDataSupport(ctj.getType(), new String[]{"key", "value"},
                                                        new Object[]{keyAndValues[i], keyAndValues[i+1]});
            data.put(cd);
        }
        return data;
    }


    private Object extract(boolean pJson,Object pValue,String ... pPathElements) throws AttributeNotFoundException {
        Stack<String> extra = new Stack<String>();
        if (pPathElements.length > 0) {
            for (String p : pPathElements) {
                extra.push(p);
            }
        }
        Collections.reverse(extra);
        return extractor.extractObject(converter,pValue,extra,pJson);
    }
}
