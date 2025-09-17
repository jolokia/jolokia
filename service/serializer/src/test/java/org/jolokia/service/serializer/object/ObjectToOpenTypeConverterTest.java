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
package org.jolokia.service.serializer.object;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.management.*;
import javax.management.openmbean.*;

import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.service.serializer.json.ObjectToJsonConverter;
import org.jolokia.service.serializer.util.CompositeTypeAndJson;
import org.jolokia.service.serializer.util.TabularTypeAndJson;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static javax.management.openmbean.SimpleType.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 03.08.11
 */
@Test
public class ObjectToOpenTypeConverterTest {

    private ObjectToOpenTypeConverter converter;
    private ObjectToJsonConverter otjc;

    @BeforeClass
    public void setup() {
        ObjectToObjectConverter objectToObjectConverter = new ObjectToObjectConverter();
        converter = new ObjectToOpenTypeConverter(objectToObjectConverter, false);
        otjc = new ObjectToJsonConverter(objectToObjectConverter, null, null);
    }

    @Test
    public void nullValue() {
        assertNull(converter.convert(SimpleType.STRING, null));
    }

    @Test
    public void simpleType() {
        assertEquals(converter.convert(SimpleType.STRING, "bla"),"bla");
        assertEquals(converter.convert(SimpleType.BOOLEAN, "true"),true);
        assertEquals(converter.convert(SimpleType.BOOLEAN, false),false);
        assertEquals(converter.convert(SimpleType.DOUBLE, 4.52),4.52);
        assertEquals(converter.convert(SimpleType.DOUBLE, "4.52"),4.52);
        assertEquals(converter.convert(SimpleType.INTEGER, "9876"),9876);
    }


    @Test(expectedExceptions = { NumberFormatException.class })
    public void simpleTypeFailed() {
        converter.convert(SimpleType.INTEGER, "4.52");
    }

    @Test
    public void arrayType() throws OpenDataException, ParseException, IOException {
        ArrayType<String> type = new ArrayType<>(1,STRING);
        String json = "[ \"hello\", \"world\" ]";
        for (Object element : new Object[] { json, new JSONParser().parse(json) }) {
            Object[] data = (Object[]) converter.convert(type, element);
            assertEquals(data.length,2);
            assertEquals(data[0],"hello");
            assertEquals(data[1],"world");
        }
    }

    @Test
    // https://github.com/jolokia/jolokia/issues/212
    public void arrayType1D() throws Exception {
        Integer[] oneDim = new Integer[]{1, 2};
        Object res = otjc.serialize(oneDim, null, SerializeOptions.DEFAULT);
        OpenType<?> type = new ArrayType<>(SimpleType.INTEGER, false);
        assertEquals(Arrays.deepToString(oneDim), Arrays.deepToString((Object[]) converter.convert(type, res)));
    }

    @Test
    // https://github.com/jolokia/jolokia/issues/212
    public void arrayType2D() throws Exception {
        Integer[] oneDim = new Integer[]{1, 2};
        Integer[][] twoDims = new Integer[][] { oneDim, { 3, 4 } };
        Object res = otjc.serialize(twoDims, null, SerializeOptions.DEFAULT);
        OpenType<?> type = new ArrayType<>(2, SimpleType.INTEGER);
        Object[] actual = ((JSONArray) res).toArray();
        Object[] expected = (Object[]) converter.convert(type, res);
        assertEquals(actual.length, expected.length);
        for (int idx = 0; idx < actual.length; idx++) {
            JSONArray actual2 = (JSONArray) actual[idx];
            Object[] expected2 = (Object[]) expected[idx];
            assertEquals(actual2.size(), expected2.length);
            for (int idx2 = 0; idx2 < actual2.size(); idx2++) {
                assertEquals(((Long) actual2.get(idx2)).intValue(), Array.get(expected[idx], idx2));
            }
        }
    }

    @Test
    // https://github.com/jolokia/jolokia/issues/212
    public void arrayType3D() throws Exception {
        // 2 elements
        int[] oneDim = new int[]{1, 2};
        // 3 x 2 elements
        int[][] twoDims = new int[][] { oneDim, { 3, 4 }, { 5, 6 } };
        // 2 x (3 x 2 elements) elements
        int[][][] threeDims = new int[][][] { twoDims, {{ 3, 4 }, { 5, 6 }, { 7, 8 } } };
        Object res = otjc.serialize(threeDims, null, SerializeOptions.DEFAULT);
        OpenType<?> type = ArrayType.getPrimitiveArrayType(int[][][].class);
        int[][][] tab = (int[][][]) converter.convert(type, res);
        assertEquals(tab.length, 2);
        assertEquals(tab[0].length, 3);
        assertEquals(tab[0][2].length, 2);
        assertEquals(otjc.serialize(threeDims, null, SerializeOptions.DEFAULT),
            otjc.serialize(tab, null, SerializeOptions.DEFAULT));
    }

    @Test
    public void arrayType4D() throws Exception {
        // 2 elements
        int[] oneDim = new int[]{1, 2};
        // 3 x 2 elements
        int[][] twoDims = new int[][] { oneDim, { 3, 4 }, { 5, 6 } };
        // 2 x (3 x 2 elements) elements
        int[][][] threeDims = new int[][][] { twoDims, {{ 3, 4 }, { 5, 6 }, { 7, 8 } } };
        // 3 x (2 x (3 x 2 elements)) elements
        int[][][][] fourDims = new int[][][][] { threeDims, threeDims, threeDims };
        Object res = otjc.serialize(fourDims, null, SerializeOptions.DEFAULT);
        OpenType<?> type = ArrayType.getPrimitiveArrayType(int[][][][].class);
        int[][][][] tab = (int[][][][]) converter.convert(type, res);
        assertEquals(otjc.serialize(fourDims, null, SerializeOptions.DEFAULT),
            otjc.serialize(tab, null, SerializeOptions.DEFAULT));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONArray.*")
    public void arrayTypeWithWrongJson() throws OpenDataException {
        converter.convert(new ArrayType<>(2, STRING), "{ \"hello\": \"world\"}");
    }

    @Test
    public void arrayTypeWithCompositeElementType() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        CompositeData[] result =
                (CompositeData[]) converter.convert(new ArrayType<>(1, taj.getType()), "[" + taj.getJsonAsString() + "]");
        assertEquals(result[0].get("verein"), "FCN");
        assertEquals(result.length,1);
    }

    @Test
    // https://github.com/jolokia/jolokia/issues/212 - we already support it
    public void arrayTypeWithNowProperElementType() throws OpenDataException {
        TabularTypeAndJson taj = new TabularTypeAndJson(
                new String[] { "verein" },
                new CompositeTypeAndJson(
                   STRING,"verein","fcn",
                   BOOLEAN,"absteiger",false
                )
        );
        JSONArray array = new JSONArray();
        array.add(taj.getJson());
        converter.convert(new ArrayType<>(1, taj.getType()), array);
    }

    @Test
    public void compositeType() throws OpenDataException {
        // Create this JSON:
        // {
        //   "verein" : "FCN",
        //   "platz" : 6,
        //   "absteiger" : false
        // }
        //
        // the CompositeType is:
        // javax.management.openmbean.CompositeType(
        //   name=testType,
        //   items=(
        //     (
        //       itemName=absteiger,
        //       itemType=javax.management.openmbean.SimpleType(name=java.lang.Boolean)
        //     ),
        //     (
        //       itemName=platz,
        //       itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)
        //     ),
        //     (
        //       itemName=trainer,
        //       itemType=javax.management.openmbean.SimpleType(name=java.lang.String)
        //     ),
        //     (
        //       itemName=verein,
        //       itemType=javax.management.openmbean.SimpleType(name=java.lang.String)
        //     )
        //   )
        // )
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
            STRING, "verein", "FCN",
            LONG, "platz", 6L,
            STRING, "trainer", null,
            BOOLEAN, "absteiger", false
        );
        for (Object input : new Object[]{taj.getJson(), taj.getJsonAsString()}) {
            CompositeData result = (CompositeData) converter.convert(taj.getType(), input);
            assertEquals(result.get("verein"), "FCN");
            assertNull(result.get("trainer"));
            assertEquals(result.get("platz"), 6L);
            assertEquals(result.get("absteiger"), false);
            assertEquals(result.values().size(), 4);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONObject.*")
    public void compositeTypeWithWrongJson() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convert(taj.getType(), "[12, 15, 16]");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*praesident.*")
    public void compositeTypeWithWrongKey() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convert(taj.getType(), "{ \"praesident\": \"hoeness\"}");
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*Bad parser state, EOF at state PARSING_VALUE.*")
    public void invalidJson() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convert(taj.getType(), "{ \"praesident\":");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*into org\\.jolokia\\.json\\.JSONObject.*")
    public void invalidJson2() throws OpenDataException {
        CompositeTypeAndJson taj = new CompositeTypeAndJson(
                STRING,"verein","FCN"

        );
        converter.convert(taj.getType(), "2");
    }

    @Test
    public void tabularMXTypes() throws Exception {
        TabularType t1 = TabularDataConverter.createMXBeanTabularType(STRING, STRING);
        assertTrue(t1.getRowType().containsKey("key"));
        assertTrue(t1.getRowType().containsKey("value"));
        assertEquals(t1.getRowType().keySet().size(), 2);
        System.out.println(t1.getTypeName());

        TabularType t2 = TabularDataConverter.createMXBeanTabularType(ArrayType.getPrimitiveArrayType(int[].class), SimpleType.DATE);
        System.out.println(t2.getTypeName());

        TabularType t3 = TabularDataConverter.createMXBeanTabularType(t1, t2);
        System.out.println(t3.getTypeName());
    }

    // tests for TabularType based on this CompositeType
    // {
    //   "verein": "fcn",   // SimpleType.STRING
    //   "absteiger": false // SimpleType.BOOLEAN
    // }
    //
    // JSON for entire TabularType has top-level keys take from "verein" keys
    // {
    //   "fcn": { // from the CompositeData
    //     "verein": "fcn",
    //     "absteiger": false
    //   },
    //   "fcb": { // from `"fcb", true`
    //     "verein": "fcb",
    //     "absteiger": true
    //   },
    //   "werder": { // from `"werder", false`
    //     "verein": "werder",
    //     "absteiger": false
    //   }
    // }
    //
    // This matches format #3 - nested index element maps

    private TabularTypeAndJson getSampleTabularType() throws OpenDataException {
        return new TabularTypeAndJson(
            new String[]{"verein"},
            new CompositeTypeAndJson(
                STRING, "verein", "fcn",
                BOOLEAN, "absteiger", false
            ),
            "fcb", true,
            "werder", false
        );
    }

    @Test
    public void tabularType() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularType();
        TabularData data = (TabularData) converter.convert(taj.getType(), taj.getJsonAsString());
        assertEquals(data.get(new String[] { "fcn" }).get("absteiger"),false);
        assertEquals(data.get(new String[] { "fcb" }).get("absteiger"),true);
    }

    @Test
    public void tabularTypeConvertFromJSONObject() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularType();
        TabularData data = (TabularData) converter.convert(taj.getType(), taj.getJson());
        assertEquals(data.get(new String[] { "fcn" }).get("absteiger"),false);
        assertEquals(data.get(new String[] { "fcb" }).get("absteiger"),true);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*praesident.*")
    public void compositeTypeWithWrongType() throws OpenDataException {
        converter.convert(getSampleTabularType().getType(), "{ \"praesident\": \"hoeness\"}");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONArray.*")
    public void compositeTypeWithWrongInnerType() throws OpenDataException {
        converter.convert(getSampleTabularType().getType(), "[[{ \"praesident\": \"hoeness\"}]]");
    }

    // tests for TabularType based on this CompositeType which builds @MXBean compatible TabularTypes
    // {
    //   "key": "dummy",  // SimpleType.STRING
    //   "value": "dummy" // SimpleType.BOOLEAN
    // }
    //
    // This TabularType allows parsing any map according to format #1

    private TabularTypeAndJson getSampleTabularTypeForMXBeanMap() throws OpenDataException {
        return new TabularTypeAndJson(
            new String[]{"key"},
            new CompositeTypeAndJson(
                STRING, "key", "dummy",
                STRING, "value", "dummy"
            )
        );
    }

    @Test
    public void tabularTypeForMXBeanMaps() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularTypeForMXBeanMap();

        // any map can be parsed into TabularData when the TabularType matches @MXBean specification for Maps
        String json = "{\"keyOne\": \"valueOne\", \"keyTwo\": \"valueTwo\"}";
        TabularData data = (TabularData) converter.convert(taj.getType(), json);
        CompositeData col1 = data.get(new String[] { "keyOne" });
        assertEquals(col1.get("key"),"keyOne");
        assertEquals(col1.get("value"),"valueOne");
        CompositeData col2 = data.get(new String[] { "keyTwo" });
        assertEquals(col2.get("key"),"keyTwo");
        assertEquals(col2.get("value"),"valueTwo");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*JSONObject.*")
    public void tabularTypeForMXBeanMapsFail() throws OpenDataException {
        TabularTypeAndJson taj = getSampleTabularTypeForMXBeanMap();

        converter.convert(taj.getType(), "[ { \"keyOne\" : \"valueOne\" } ]");
    }

    @Test
    public void tabularTypeForMXBeanMapsComplex() throws OpenDataException {
        TabularTypeAndJson inner = getSampleTabularTypeForMXBeanMap();
        TabularTypeAndJson taj = new TabularTypeAndJson(
            new String[]{"key"},
            new CompositeTypeAndJson(
                STRING, "key", null,
                // the value is of TabularType, but still matches @MXBean format (key/value items)
                inner.getType(), "value", null
            )
        );

        String json = "{\"keyOne\": {\"innerKeyOne\": \"valueOne\"}, \"keyTwo\": {\"innerKeyTwo\": \"valueTwo\"}}";
        TabularData data = (TabularData) converter.convert(taj.getType(), json);
        CompositeData col1 = data.get(new String[] { "keyOne" });
        assertEquals(col1.get("key"),"keyOne");
        TabularData innerCol1 = (TabularData) col1.get("value");
        CompositeData col1inner = innerCol1.get(new String[]{"innerKeyOne"});
        assertEquals(col1inner.get("key"),"innerKeyOne");
        assertEquals(col1inner.get("value"),"valueOne");
    }

    private TabularType getSampleTabularTypeForComplexTabularData() throws OpenDataException {
        CompositeType keyType = new CompositeType("key","key",
            new String[] { "name", "age"},
            new String[] { "name", "age"},
            new OpenType[] { STRING, LONG});
        CompositeTypeAndJson ctj = new CompositeTypeAndJson(
            keyType,"user",null,
            STRING,"street",null,
            OBJECTNAME,"oname",null
        );
        return new TabularType("test","test",ctj.getType(),new String[] {"user", "street"} );
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*given as Maps or JSONObjects, not org.jolokia.json.JSONArray.*")
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
        converter.convert(type, json);
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
        TabularData data = (TabularData) converter.convert(type, json);
        assertNotNull(data);
        Set<?> keySet = data.keySet();
        assertEquals(keySet.size(), 1);
        List<?> keys = (List<?>) keySet.iterator().next();
        assertEquals(keys.size(),2);
        assertTrue(keys.contains("homestreet"));
        CompositeData cd = checkCompositeKey(keys);
        CompositeData row = data.get(new Object[] { cd, "homestreet"});
        assertEquals(row.get("user"),cd);
        assertEquals(row.get("street"),"homestreet");
        assertEquals(row.get("oname"),new ObjectName("java.lang:type=Memory"));
    }

    private CompositeData checkCompositeKey(List<?> pKeys) {
        for (Object o : pKeys) {
            if (o instanceof CompositeData) {
                CompositeData cd = (CompositeData) o;
                assertEquals(cd.get("name"),"roland");
                assertEquals(cd.get("age"),44L);
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
        converter.convert(type, json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*expected 2 entries, found 3 entries.*")
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
        converter.convert(type, json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*must be a Collection<String>, not org.jolokia.json.JSONObject.*")
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
        converter.convert(type, json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*must be a Collection<\\?>, not org.jolokia.json.JSONObject.*")
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
        converter.convert(type, json);
    }

    @Test
    public void multipleLevelTabularData() throws OpenDataException {
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
        TabularData data = (TabularData) converter.convert(type, map);
        CompositeData row = data.get(new Object[] { "fcn", "franconia" });
        assertNotNull(row);
        assertFalse((Boolean) row.get("absteiger"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*No converter.*")
    public void unknownOpenType() throws OpenDataException {
        converter.convert(new OpenType<>("java.util.Date", "guenther", "guenther") {
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
        }, "bla");
    }

}
