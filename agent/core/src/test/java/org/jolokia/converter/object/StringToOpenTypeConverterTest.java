package org.jolokia.converter.object;

import java.util.List;
import java.util.Set;

import javax.management.*;
import javax.management.openmbean.*;

import org.jolokia.converter.util.CompositeTypeAndJson;
import org.jolokia.converter.util.TabularTypeAndJson;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static javax.management.openmbean.SimpleType.*;
import static org.testng.Assert.*;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

/**
 * @author roland
 * @since 03.08.11
 */
@Test
public class StringToOpenTypeConverterTest {

    private StringToOpenTypeConverter converter;


    @BeforeTest
    public void setup() {

        StringToObjectConverter stringToObjectConverter = new StringToObjectConverter();
        converter = new StringToOpenTypeConverter(stringToObjectConverter);
    }

    @Test
    public void nullValue() throws OpenDataException {
        assertNull(converter.convertToObject(SimpleType.STRING,null));
    }


    @Test
    public void simpleType() {
        assertTrue(converter.canConvert(SimpleType.STRING));
        assertEquals(converter.convertToObject(SimpleType.STRING,"bla"),"bla");
        assertEquals(converter.convertToObject(SimpleType.BOOLEAN,"true"),true);
        assertEquals(converter.convertToObject(SimpleType.BOOLEAN,false),false);
        assertEquals(converter.convertToObject(SimpleType.DOUBLE,4.52),4.52);
        assertEquals(converter.convertToObject(SimpleType.DOUBLE,"4.52"),4.52);
        assertEquals(converter.convertToObject(SimpleType.INTEGER,"9876"),9876);
    }


    @Test(expectedExceptions = { NumberFormatException.class })
    public void simpleTypeFailed() {
        converter.convertToObject(SimpleType.INTEGER,"4.52");
    }


    @Test
    public void arrayType() throws OpenDataException, ParseException {
        ArrayType type = new ArrayType(2,STRING);
        String json = "[ \"hello\", \"world\" ]";
        for (Object element : new Object[] { json, new JSONParser().parse(json) }) {
            Object[] data = (Object[]) converter.convertToObject(type,element);
            assertEquals(data.length,2);
            assertEquals(data[0],"hello");
            assertEquals(data[1],"world");
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONArray.*")
    public void arrayTypeWithWrongJson() throws OpenDataException {
        converter.convertToObject(new ArrayType(2,STRING),"{ \"hello\": \"world\"}");
    }

    @Test
    public void arrayTypeWithCompositeElementType() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        CompositeData[] result =
                (CompositeData[]) converter.convertToObject(new ArrayType(2,taj.getType()),"[" + taj.getJsonAsString() + "]");
        assertEquals(result[0].get("verein"), "FCN");
        assertEquals(result.length,1);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class,expectedExceptionsMessageRegExp = ".*Unsupported.*")
    public void arrayTypeWithWrongElementType() throws OpenDataException {
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[] { "verein" },
                new CompositeTypeAndJson(
                   STRING,"verein","fcn",
                   BOOLEAN,"absteiger",false
                )
        );
        JSONArray array = new JSONArray();
        array.add(taj.getJson());
        converter.convertToObject(new ArrayType(2, taj.getType()),array);
    }

    @Test
    public void compositeType() throws OpenDataException, AttributeNotFoundException, ParseException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN",
                INTEGER,"platz",6,
                STRING,"trainer",null,
                BOOLEAN,"absteiger",false
        );
        for (Object input : new Object[] { taj.getJson(), taj.getJsonAsString() }) {
            CompositeData result = (CompositeData) converter.convertToObject(taj.getType(),input);
            assertEquals(result.get("verein"),"FCN");
            assertEquals(result.get("trainer"),null);
            assertEquals(result.get("platz"),6);
            assertEquals(result.get("absteiger"),false);
            assertEquals(result.values().size(),4);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONObject.*")
    public void compositeTypeWithWrongJson() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convertToObject(taj.getType(),"[ 12, 15, 16]");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*praesident.*")
    public void compositeTypeWithWrongKey() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convertToObject(taj.getType(),"{ \"praesident\": \"hoeness\"}");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*parse.*")
    public void invalidJson() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convertToObject(taj.getType(),"{ \"praesident\":");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONAware.*")
    public void invalidJson2() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convertToObject(taj.getType(),"2");
    }


    @Test
    public void tabularType() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularType();
        TabularData data = (TabularData) converter.convertToObject(taj.getType(),taj.getJsonAsString());
        assertEquals(data.get(new String[] { "fcn" }).get("absteiger"),false);
        assertEquals(data.get(new String[] { "fcb" }).get("absteiger"),true);
    }

    @Test
    public void tabularTypeForMXBeanMaps() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularTypeForMXBeanMap();

        String json = "{ \"keyOne\" : \"valueOne\", \"keyTwo\" : \"valueTwo\"}";
        TabularData data = (TabularData) converter.convertToObject(taj.getType(),json);
        CompositeData col1 = data.get(new String[] { "keyOne" });
        assertEquals(col1.get("key"),"keyOne");
        assertEquals(col1.get("value"),"valueOne");
        CompositeData col2 = data.get(new String[] { "keyTwo" });
        assertEquals(col2.get("key"),"keyTwo");
        assertEquals(col2.get("value"),"valueTwo");
    }

    @Test
    public void tabularTypeInFullRepresentation() throws OpenDataException, MalformedObjectNameException {
        TabularType type = getSampleTabularTypeForComplexTabularData();
        String json = "{ \"indexNames\" : [ \"user\", \"street\" ], " +
                      "  \"values\" : [ " +
                      "      { \"user\" : { \"name\" : \"roland\", \"age\" : 44 }, " +
                      "        \"street\" : \"homestreet\", " +
                      "        \"oname\" : \"java.lang:type=Memory\" " +
                      "      }]" +
                      "}";
        TabularData data = (TabularData) converter.convertToObject(type,json);
        assertNotNull(data);
        Set keySet = data.keySet();
        assertEquals(keySet.size(), 1);
        List keys = (List) keySet.iterator().next();
        assertEquals(keys.size(),2);
        assertTrue(keys.contains("homestreet"));
        CompositeData cd = checkCompositeKey(keys);
        CompositeData row = data.get(new Object[] { cd, "homestreet"});
        assertEquals(row.get("user"),cd);
        assertEquals(row.get("street"),"homestreet");
        assertEquals(row.get("oname"),new ObjectName("java.lang:type=Memory"));
    }

    private CompositeData checkCompositeKey(List pKeys) {
        for (Object o : pKeys) {
            if (o instanceof CompositeData) {
                CompositeData cd = (CompositeData) o;
                assertEquals(cd.get("name"),"roland");
                assertEquals(cd.get("age"),44);
                return cd;
            }
        }
        fail("No CD Key found");
        return null;
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*index.*name.*")
    public void invalidIndexNameForComplexTabularDataConversion() throws OpenDataException {
        TabularType type = getSampleTabularTypeForComplexTabularData();
        String json = "{ \"indexNames\" : [ \"user\", \"bla\" ], " +
                      "  \"values\" : [ " +
                      "      { \"user\" : { \"name\" : \"roland\", \"age\" : 44 }, " +
                      "        \"bla\" : \"homestreet\", " +
                      "        \"oname\" : \"java.lang:type=Memory\" " +
                      "      }]" +
                      "}";
        converter.convertToObject(type,json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*index.*name.*")
    public void invalidIndexNameCountForComplexTabularDataConversion() throws OpenDataException {
        TabularType type = getSampleTabularTypeForComplexTabularData();
        String json = "{ \"indexNames\" : [ \"user\", \"street\", \"bla\" ], " +
                      "  \"values\" : [ " +
                      "      { \"user\" : { \"name\" : \"roland\", \"age\" : 44 }, " +
                      "        \"street\" : \"homestreet\", " +
                      "        \"bla\" : \"blub\", " +
                      "        \"oname\" : \"java.lang:type=Memory\" " +
                      "      }]" +
                      "}";
        converter.convertToObject(type,json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*array.*")
    public void invalidTypeComplexTabularDataConversion1() throws OpenDataException {
        TabularType type = getSampleTabularTypeForComplexTabularData();
        String json = "{ \"indexNames\" : { \"user\" : \"bla\" }, " +
                      "  \"values\" : [ " +
                      "      { \"user\" : { \"name\" : \"roland\", \"age\" : 44 }, " +
                      "        \"street\" : \"homestreet\", " +
                      "        \"bla\" : \"blub\", " +
                      "        \"oname\" : \"java.lang:type=Memory\" " +
                      "      }]" +
                      "}";
        converter.convertToObject(type,json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*array.*")
    public void invalidTypeComplexTabularDataConversion2() throws OpenDataException {
        TabularType type = getSampleTabularTypeForComplexTabularData();
        String json = "{ \"indexNames\" : [ \"user\", \"street\" ], " +
                      "  \"values\" :  " +
                      "      { \"user\" : { \"name\" : \"roland\", \"age\" : 44 }, " +
                      "        \"street\" : \"homestreet\", " +
                      "        \"bla\" : \"blub\", " +
                      "        \"oname\" : \"java.lang:type=Memory\" " +
                      "      }" +
                      "}";
        converter.convertToObject(type,json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*object.*")
    public void invalidTypeComplexTabularDataConversion3() throws OpenDataException {
        TabularType type = getSampleTabularTypeForComplexTabularData();
        String json = "{ \"indexNames\" : [ \"user\", \"street\"], " +
                      "  \"values\" : [ " +
                      "      [{ \"user\" : { \"name\" : \"roland\", \"age\" : 44 }, " +
                      "        \"street\" : \"homestreet\", " +
                      "        \"bla\" : \"blub\", " +
                      "        \"oname\" : \"java.lang:type=Memory\" " +
                      "      }]]" +
                      "}";
        converter.convertToObject(type,json);
    }




    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONObject.*")
    public void tabularTypeForMXBeanMapsFail() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularTypeForMXBeanMap();

        converter.convertToObject(taj.getType(), "[ { \"keyOne\" : \"valueOne\" } ]");
    }

    @Test
    public void tabularTypeForMXBeanMapsComplex() throws OpenDataException {
        TabularTypeAndJson inner = getSampleTabularTypeForMXBeanMap();
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[]{"key"},
                new CompositeTypeAndJson(
                        STRING, "key", null,
                        inner.getType(), "value", null
                )
        );

        String json = "{ \"keyOne\" : { \"innerKeyOne\" : \"valueOne\" }, \"keyTwo\" : { \"innerKeyTwo\" : \"valueTwo\"}}";
        TabularData data = (TabularData) converter.convertToObject(taj.getType(),json);
        CompositeData col1 = data.get(new String[] { "keyOne" });
        assertEquals(col1.get("key"),"keyOne");
        TabularData innerCol1 = (TabularData) col1.get("value");
        CompositeData col1inner = innerCol1.get(new String[]{"innerKeyOne"});
        assertEquals(col1inner.get("key"),"innerKeyOne");
        assertEquals(col1inner.get("value"),"valueOne");
    }


    @Test
    public void multipleLevleTabularData() throws OpenDataException {
        JSONObject map = new JSONObject();
        JSONObject inner = new JSONObject();
        map.put("fcn",inner);
        JSONObject innerinner = new JSONObject();
        inner.put("franconia",innerinner);
        innerinner.put("verein","fcn");
        innerinner.put("region","franconia");
        innerinner.put("absteiger",false);

        TabularType type = new TabularType("soccer","soccer",
                                           new CompositeType("row","row",
                                                             new String[] { "verein", "region", "absteiger" },
                                                             new String[] { "verein","region","absteiger"},
                                                             new OpenType[] { STRING, STRING, BOOLEAN}),
                                           new String[] { "verein", "region" });
        TabularData data = (TabularData) converter.convertToObject(type,map);
        CompositeData row = data.get(new Object[] { "fcn", "franconia" });
        assertNotNull(row);
        assertFalse((Boolean) row.get("absteiger"));
    }


    private TabularTypeAndJson getSampleTabularTypeForMXBeanMap() throws OpenDataException {
        return new TabularTypeAndJson(
                    new String[] { "key" },
                    new CompositeTypeAndJson(
                            STRING,"key","dummy",
                            STRING,"value", "dummy"
                    )
            );
    }



    private TabularType getSampleTabularTypeForComplexTabularData() throws OpenDataException {
        CompositeType keyType = new CompositeType("key","key",
                                                  new String[] { "name", "age"},
                                                  new String[] { "name", "age"},
                                                  new OpenType[] { STRING, INTEGER});
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
                keyType,"user",null,
                STRING,"street",null,
                OBJECTNAME,"oname",null
        );
        return new TabularType("test","test",ctj.getType(),new String[] {"user", "street"} );
    }




    private TabularTypeAndJson getSampleTabularType() throws OpenDataException {
        return new TabularTypeAndJson(
                    new String[] { "verein" },
                    new CompositeTypeAndJson(
                       STRING,"verein","fcn",
                       BOOLEAN,"absteiger",false
                    ),
                    "fcb",true,
                    "werder",false
            );
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*praesident.*")
    public void compositeTypeWithWrongType() throws OpenDataException {
        converter.convertToObject(getSampleTabularType().getType(),"{ \"praesident\": \"hoeness\"}");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONArray.*")
    public void compositeTypeWithWrongInnerType() throws OpenDataException {
        converter.convertToObject(getSampleTabularType().getType(),"[[{ \"praesident\": \"hoeness\"}]]");
    }


    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*No converter.*")
    public void unknownOpenType() throws OpenDataException {
        converter.convertToObject(new OpenType("java.util.Date","guenther","guenther") {
            @Override
            public boolean isValue(Object obj) {
                return false;
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public String toString() {
                return null;
            }
        },"bla");
    }
}
