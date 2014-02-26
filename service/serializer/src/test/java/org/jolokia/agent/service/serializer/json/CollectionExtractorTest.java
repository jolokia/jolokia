package org.jolokia.agent.service.serializer.json;

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

import org.jolokia.agent.service.serializer.object.StringToObjectConverter;
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

    @BeforeClass
    public void setup() {
        extractor = new CollectionExtractor();
        converter = new ObjectToJsonConverter(new StringToObjectConverter(),null);
        converter.setupContext();
    }

    @Test
    public void json() throws AttributeNotFoundException {
        assertFalse(extractor.canSetValue());
        Set set = new HashSet(Arrays.asList("jolokia","habanero"));
        Stack paths = new Stack();
        paths.add("1");
        for (List ret : new List[] {
                (List) extractor.extractObject(converter,set,null,true),
                (List) extractor.extractObject(converter,set,paths,true),
        }) {
            assertEquals(ret.size(),2);
            assertTrue(ret.contains("jolokia"));
            assertTrue(ret.contains("habanero"));
            assertTrue(ret instanceof JSONArray);
        }
    }


    @Test
    public void noJson() throws AttributeNotFoundException {
        Set set = new HashSet(Arrays.asList("jolokia","habanero"));
        Set ret = (Set) extractor.extractObject(converter,set,null,false);
        assertEquals(ret,set);
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void failed() throws InvocationTargetException, IllegalAccessException {
        extractor.setObjectValue(null,null,null,null);
    }
}
