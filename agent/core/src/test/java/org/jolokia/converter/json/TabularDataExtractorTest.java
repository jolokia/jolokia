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

    TabularDataExtractor extractor = new TabularDataExtractor();

    ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        converter = new ObjectToJsonConverter(new StringToObjectConverter(),null);
        converter.setupContext();
    }

    @AfterMethod
    public void tearDown() {
        if (converter != null) {
            converter.clearContext();
        }
    }
    @Test
    public void typeInfo() {
        assertEquals(extractor.getType(), TabularData.class);
        assertFalse(extractor.canSetValue());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*written to.*")
    public void setValue() throws InvocationTargetException, IllegalAccessException {
        extractor.setObjectValue(new StringToObjectConverter(),new Object(), "bla","blub");
    }

    @Test
    public void extractMapAsJson() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING,"key1");
        JSONObject result = (JSONObject) extract(true,data);
        assertNull(result.get("key2"));
        assertEquals(result.get("key1"),"value1");
        assertEquals(result.size(),1);
    }

    @Test
    public void extractMapWithComplexType() throws OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson cdj = new CompositeTypeAndJson(STRING, "name", "roland", INTEGER, "date", 1968);
        TabularData data = getMapTabularData(cdj.getType(),cdj.getCompositeData());
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

        assertEquals(value.get("value"), "value1");
        assertEquals(key.size(),2);
    }

    @Test
    void extractMapDirect() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING,"key1");
        TabularData data2 = (TabularData) extract(false,data);
        assertEquals(data2,data);
    }

    @Test
    void extractMapWithPath() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getMapTabularData(STRING,"key1");
        Object result = extract(true,data,"key1");
        assertEquals(result,"value1");
    }
    @Test
    void extractGenericTabularDataWithJson() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getComplextTabularData();
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
        TabularData data = getComplextTabularData();
        TabularData data2 = (TabularData) extract(false, data);
        assertEquals(data2,data);

    }

    @Test
    void extractGenericTabularDataWithPath() throws OpenDataException, AttributeNotFoundException {
        TabularData data = getComplextTabularData();
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

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*name.*firstname.*")
    void extractGenericTabularDataWithToShortPath() throws OpenDataException, AttributeNotFoundException {
        extract(true, getComplextTabularData(), "meyer");
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



    private TabularData getComplextTabularData() throws OpenDataException {
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


    private TabularData getMapTabularData(OpenType keyType, Object keyValue) throws OpenDataException {
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
                keyType,"key",null,
                STRING,"value",null
        );

        TabularTypeAndJson taj = new TabularTypeAndJson(new String[] { "key" },ctj);
        TabularData data = new TabularDataSupport(taj.getType());
        CompositeData cd = new CompositeDataSupport(ctj.getType(),new String[] { "key", "value" },
                                                    new Object[] { keyValue, "value1" });
        data.put(cd);
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
