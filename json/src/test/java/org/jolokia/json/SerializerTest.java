/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.json;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class SerializerTest {

    @Test
    public void serilizeCharactersWithEscaping() throws IOException {
        StringWriter sw = new StringWriter();
        JSONWriter.serialize('\b', sw);
        assertEquals(sw.toString(), "\"\\b\"");

        sw = new StringWriter();
        JSONWriter.serialize((char) 0x04, sw);
        assertEquals(sw.toString(), "\"\\u0004\"");

        sw = new StringWriter();
        JSONWriter.serialize('"', sw);
        assertEquals(sw.toString(), "\"\\\"\"");

        sw = new StringWriter();
        JSONWriter.serialize('\\', sw);
        assertEquals(sw.toString(), "\"\\\\\"");

        sw = new StringWriter();
        JSONWriter.serialize('ツ', sw);
        assertEquals(sw.toString(), "\"ツ\"");

        sw = new StringWriter();
        JSONWriter.serialize('ń', sw);
        assertEquals(sw.toString(), "\"ń\"");

        sw = new StringWriter();
        JSONWriter.serialize('/', sw);
        assertEquals(sw.toString(), "\"/\"");
    }

    @Test
    public void serializeStrings() throws IOException {
        StringWriter sw = new StringWriter();
        JSONWriter.serialize("Hello World! ąあ\n\\\"/", sw);
        assertEquals("\"Hello World! ąあ\\n\\\\\\\"/\"", sw.toString());
    }

    @Test
    public void serializeArrays() throws IOException {
        StringWriter sw = new StringWriter();
        JSONWriter.serialize(new String[] { "hello", "world" }, sw);
        assertEquals(sw.toString(), "[\"hello\",\"world\"]");
    }

    @Test
    public void serializeEmptyArrays() throws IOException {
        StringWriter sw = new StringWriter();
        JSONWriter.serialize(new String[] { }, sw);
        assertEquals(sw.toString(), "[]");

        sw = new StringWriter();
        JSONWriter.serialize(new byte[] { }, sw);
        assertEquals(sw.toString(), "[]");
    }

    @Test
    public void serializeCollection() throws IOException {
        StringWriter sw = new StringWriter();
        JSONArray array = new JSONArray();
        array.add(1);
        array.add("hello");
        array.add(false);
        array.add(null);
        array.add(1.3);
        JSONWriter.serialize(array, sw);
        assertEquals(sw.toString(), "[1,\"hello\",false,null,1.3]");
    }

    @Test
    public void serializeNiceMap() throws IOException {
        StringWriter sw = new StringWriter();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("k1", 1);
        map.put("k2", "hello");
        map.put("k3", false);
        map.put("k4", null);
        map.put("k5", 1.3);
        JSONWriter.serialize(map, sw);
        assertEquals(sw.toString(), "{\"k1\":1,\"k2\":\"hello\",\"k3\":false,\"k4\":null,\"k5\":1.3}");
    }

    @Test
    public void serializeAnyMap() throws IOException {
        StringWriter sw = new StringWriter();
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(42, 1);
        map.put(null, "hello");
        map.put(new File("/"), false);
        JSONWriter.serialize(map, sw);
        if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
        	assertEquals(sw.toString(), "{\"42\":1,\"\":\"hello\",\"\\\\\":false}");
        } else {
        	assertEquals(sw.toString(), "{\"42\":1,\"\":\"hello\",\"/\":false}");
        }
    }

    @Test
    public void serializeNestedArrays() throws IOException {
        StringWriter sw = new StringWriter();
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(Arrays.asList(2, 3));
        list.add(Arrays.asList(Arrays.asList(4, 5), new int[] { 6, 7 }));
        list.add(8);
        JSONWriter.serialize(list, sw);
        assertEquals(sw.toString(), "[1,[2,3],[[4,5],[6,7]],8]");
    }

}
