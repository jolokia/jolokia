package org.jolokia.converter.json;

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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONArray;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 18.10.11
 */
public class CollectionExtractorTest {

    CollectionExtractor extractor;
    private ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        extractor = new CollectionExtractor();
        converter = new ObjectToJsonConverter(new StringToObjectConverter());
        converter.setupContext();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        converter.clearContext();
    }

    @Test
    public void json() throws AttributeNotFoundException {
        assertFalse(extractor.canSetValue());
        Set set = new HashSet(Arrays.asList("jolokia","habanero"));
        List ret = (List) extractor.extractObject(converter,set,new Stack<String>(),true);
        assertEquals(ret.size(),2);
        assertTrue(ret.contains("jolokia"));
        assertTrue(ret.contains("habanero"));
        assertTrue(ret instanceof JSONArray);
    }

    @Test
    public void jsonAndPath() throws AttributeNotFoundException {
        Collection collection = Arrays.asList("jolokia","habanero");

        Stack paths = new Stack();
        paths.add("1");

        String val = (String) extractor.extractObject(converter,collection,paths,true);
        assertEquals(val, "habanero");
    }

    @Test
    public void jsonAndInvalidPaths() throws AttributeNotFoundException {
        Collection collection = Arrays.asList("jolokia","habanero");


        for (String path : new String[] { "bla", "2"}) {
            Stack<String> paths = new Stack();
            paths.add(path);
            try {
                extractor.extractObject(converter, collection, paths, true);
                fail();
            } catch (AttributeNotFoundException exp) {

            }
        }
    }


    @Test
    public void noJson() throws AttributeNotFoundException {
        Set set = new HashSet(Arrays.asList("jolokia","habanero"));
        Set ret = (Set) extractor.extractObject(converter,set,new Stack<String>(),false);
        assertEquals(ret,set);
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void failed() throws InvocationTargetException, IllegalAccessException {
        extractor.setObjectValue(null,null,null,null);
    }
}
