package org.jolokia.converter.object;

import java.util.ArrayList;
import java.util.Arrays;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.*;
import javax.naming.event.NamingExceptionEvent;

import com.sun.jdi.NativeMethodException;
import com.sun.tools.corba.se.idl.toJavaPortable.StringGen;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import sun.net.idn.StringPrep;

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

    private ObjectToJsonConverter toJsonConverter;

    @BeforeTest
    public void setup() {

        StringToObjectConverter stringToObjectConverter = new StringToObjectConverter();
        converter = new StringToOpenTypeConverter(stringToObjectConverter);
        toJsonConverter = new ObjectToJsonConverter(stringToObjectConverter,null);
    }

    @Test
    public void nullValue() throws OpenDataException {
        assertNull(converter.convertToObject(SimpleType.STRING,null));
    }

    @Test
    public void simpleType() {
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
        assertEquals(result[0].get("verein"),"FCN");
        assertNull(result[1]);
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
        converter.convertToObject(new ArrayType(2, taj.getType()),taj.getJsonAsString());
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

    // ============================================================================================================

    private static class CompositeTypeAndJson {
        JSONObject json;
        CompositeType type;
        String keys[];

        private CompositeTypeAndJson(Object... elements) throws OpenDataException {
            keys = new String[elements.length / 3];
            OpenType types[] = new OpenType[elements.length / 3];
            Object values[] = new Object[elements.length / 3];
            int j = 0;
            for (int i = 0; i < elements.length; i+=3) {
                types[j] = (OpenType) elements[i];
                keys[j] = (String) elements[i+1];
                values[j] = elements[i+2];
                j++;
            }
            type = new CompositeType(
                    "testType",
                    "Type for testing",
                    keys,
                    keys,
                    types
                    );
            json = new JSONObject();
            for (int i=0; i<keys.length;i++) {
                if (values[i] != null) {
                    json.put(keys[i],values[i]);
                }
            }
        }

        public String getKey(int idx) {
            return keys[idx];
        }

        public JSONObject getJson() {
            return json;
        }

        public CompositeType getType() {
            return type;
        }

        public String getJsonAsString() {
            return json.toJSONString();
        }
    }

    private class TabularTypeAndJson {
        TabularType type;
        JSONArray json;

        public TabularTypeAndJson(String[] index, CompositeTypeAndJson taj,Object ... rowVals) throws OpenDataException {
            CompositeType cType = taj.getType();
            json = new JSONArray();
            json.add(taj.getJson());
            int nrCols = cType.keySet().size();
            for (int i = 0 ; i < rowVals.length; i += nrCols) {
                JSONObject row = new JSONObject();
                for (int j = 0; j < nrCols; j++) {
                    row.put(taj.getKey(j),rowVals[i+j]);
                }
                json.add(row);
            }
            System.out.println(json.toJSONString());
            type = new TabularType("test","test",cType,index);
        }

        public TabularType getType() {
            return type;
        }

        public JSONArray getJson() {
            return json;
        }

        public String getJsonAsString() {
            return json.toJSONString();
        }
    }
}
