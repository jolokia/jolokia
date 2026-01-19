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
package org.jolokia.converter.json;

import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.jolokia.json.JSONArray;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 18.10.11
 */
public class CollectionAccessorTest {

    org.jolokia.converter.json.CollectionAccessor extractor;
    private org.jolokia.converter.json.ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        extractor = new org.jolokia.converter.json.CollectionAccessor();
        converter = new org.jolokia.converter.json.ObjectToJsonConverter(new ObjectToObjectConverter(), null, null);
        converter.setupContext(new SerializeOptions.Builder()
            .maxCollectionSize(5)
            .build());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        converter.clearContext();
    }

    @Test
    public void json() throws AttributeNotFoundException {
        assertFalse(extractor.canSetValue());
        Set<String> set = new HashSet<>(Arrays.asList("jolokia","habanero"));
        List<?> ret = (List<?>) extractor.extractObject(converter, set, new LinkedList<>(), true);
        assertEquals(ret.size(),2);
        assertTrue(ret.contains("jolokia"));
        assertTrue(ret.contains("habanero"));
        assertTrue(ret instanceof JSONArray);
    }

    @Test
    public void tooLong() throws AttributeNotFoundException {
        JSONArray res = (JSONArray) extractor.extractObject(converter,
            Arrays.asList("eins", "zwei", "drei", "vier", "funf", "sechs"), new LinkedList<>(), true);
        assertEquals(res.size(), 5);
        assertTrue(res.contains("eins"));
        assertTrue(res.contains("zwei"));
        assertTrue(res.contains("drei"));
        assertTrue(res.contains("vier"));
        assertTrue(res.contains("funf"));
    }

    @Test
    public void jsonAndPath() throws AttributeNotFoundException {
        Collection<String> collection = Arrays.asList("jolokia","habanero");

        Deque<String> paths = new LinkedList<>();
        paths.add("1");

        String val = (String) extractor.extractObject(converter,collection,paths,true);
        assertEquals(val, "habanero");
    }

    @Test
    public void jsonAndInvalidPaths() {
        Collection<String> collection = Arrays.asList("jolokia","habanero");

        for (String path : new String[] { "bla", "2"}) {
            Deque<String> paths = new LinkedList<>();
            paths.add(path);
            try {
                extractor.extractObject(converter, collection, paths, true);
                fail();
            } catch (AttributeNotFoundException ignored) {
            }
        }
    }

    @Test
    public void noJson() throws AttributeNotFoundException {
        Set<String> set = Set.of("jolokia","habanero");
        Set<?> ret = (Set<?>) extractor.extractObject(converter, set, new LinkedList<>(), false);
        assertEquals(ret,set);
    }

    @Test(expectedExceptions = {UnsupportedOperationException.class})
    public void failed() {
        extractor.setObjectValue(null,null,null,null);
    }

}
