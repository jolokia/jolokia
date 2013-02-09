package org.jolokia.client.request;

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

import java.io.File;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 27.03.11
 */

public class JsonSerializationTest {

    @Test
    public void nullSerialization() {
        Object result = serialize(null);
        assertNull(result);
    }


    @Test
    public void jsonAwareSerialization() {
        JSONObject arg = new JSONObject();
        Object result = serialize(arg);
        assertTrue(arg == result);
    }

    @Test
    public void arraySerialization() {
        int[] arg = new int[] { 1,2,3 };
        Object result = serialize(arg);
        assertTrue(result instanceof JSONArray);
        JSONArray res = (JSONArray) result;
        assertEquals(res.get(0),1);
        assertEquals(res.size(),3);
    }

    @Test
    public void mapSerialization() {
        Map arg = new HashMap();
        arg.put("arg",10.0);
        Object result = serialize(arg);
        assertTrue(result instanceof JSONObject);;
        JSONObject res = (JSONObject) result;
        assertEquals(res.get("arg"),10.0);
    }

    @Test
    public void collectionSerialization() {
        Set arg = new HashSet();
        arg.add(true);
        Object result = serialize(arg);
        assertTrue(result instanceof JSONArray);
        JSONArray res = (JSONArray) result;
        assertEquals(res.size(),1);
        assertEquals(res.get(0),true);
    }

    @Test
    public void complexSerialization() throws ParseException {
        Map arg = new HashMap();
        List inner = new ArrayList();
        inner.add(null);
        inner.add(new File("tmp"));
        inner.add(10);
        inner.add(42.0);
        inner.add(false);
        arg.put("first","fcn");
        arg.put("second",inner);
        Object result = serialize(arg);
        assertTrue(result instanceof JSONObject);
        JSONObject res = (JSONObject) result;
        assertEquals(res.get("first"),"fcn");
        assertTrue(res.get("second") instanceof JSONArray);
        JSONArray arr = (JSONArray) res.get("second");
        assertEquals(arr.size(),5);
        assertNull(arr.get(0));
        assertEquals(arr.get(1), "tmp");
        assertEquals(arr.get(2),10);
        assertEquals(arr.get(3),42.0);
        assertEquals(arr.get(4),false);
        String json = res.toJSONString();
        assertNotNull(json);
        JSONObject reparsed = (JSONObject) new JSONParser().parse(json);
        assertNotNull(reparsed);
        assertEquals(((List) reparsed.get("second")).get(1),"tmp");
    }


    // =====================================================================================================


    private Object serialize(Object o) {
        J4pRequest req = new J4pRequest(J4pType.VERSION,null) {
            @Override
            List<String> getRequestParts() {
                return null;
            }

            @Override
            <R extends J4pResponse<? extends J4pRequest>> R createResponse(JSONObject pResponse) {
                return null;
            }
        };
        return req.serializeArgumentToJson(o);
    }


}

