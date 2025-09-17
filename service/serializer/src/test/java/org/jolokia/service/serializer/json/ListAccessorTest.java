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
package org.jolokia.service.serializer.json;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONArray;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ListAccessorTest extends AbstractObjectAccessorTest {

    @Test
    public void testSimple() throws Exception {
        JSONArray result = (JSONArray) extractJson(Arrays.asList("eins","zwei","drei"));
        assertEquals(result.size(), 3);
        assertEquals(result.get(0),"eins");
        assertEquals(result.get(1),"zwei");
        assertEquals(result.get(2),"drei");
    }

    @Test
    public void tooLong() throws AttributeNotFoundException {
        JSONArray res = (JSONArray) extractJson(Arrays.asList("eins", "zwei", "drei", "vier", "funf", "sechs"));
        assertEquals(res.size(), 5);
        assertTrue(res.contains("eins"));
        assertTrue(res.contains("zwei"));
        assertTrue(res.contains("drei"));
        assertTrue(res.contains("vier"));
        assertTrue(res.contains("funf"));
    }

    @Test
    public void testWithPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        assertEquals(extractJson(names, "0", "domain"), "java.lang");
        assertEquals(extractJson(names, "1", "domain"), "test");
    }

    @Test
    public void testWithPathForEach() throws Exception {
        List<ObjectName> names = getObjectNameList();
        Object domains = extractJson(names, null, "domain");
        assertTrue(domains instanceof JSONArray);
        JSONArray domainsArray = (JSONArray) domains;
        assertEquals(domainsArray.get(0), "java.lang");
        assertEquals(domainsArray.get(1), "test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidInnerPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        extractJson(names, "1", "Yippie!");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        extractJson(names, "4", "domain");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithNoNumericPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        extractJson(names, "bla");
    }

    private List<ObjectName> getObjectNameList() throws MalformedObjectNameException {
        return Arrays.asList(new ObjectName("java.lang:type=Memory"),
                             new ObjectName("test:type=blub"));
    }

    @Test
    public void testWildCardPath() throws Exception {
        List<Object> mixed = createMixedList();
        JSONArray result = (JSONArray) extractJson(mixed,null,"domain");
        // only two, because there's no "domain" attribute on String and null items
        assertEquals(result.size(),2);
        assertEquals(result.get(0),"java.lang");
        assertEquals(result.get(1),"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWildCardPathAndInvalidEnd() throws Exception {
        List<Object> mixed = createMixedList();
        extractJson(mixed, null, "NoNoNo!");
    }

    private List<Object> createMixedList() throws MalformedObjectNameException {
        return Arrays.asList(new ObjectName("java.lang:type=Memory"),
                             "blub",
                             null,
                             new ObjectName("test:type=blub"));
    }

    @Test
    public void testSetValue() throws Exception {
        assertTrue(objectAccessor.canSetValue());
        List<String> list = Arrays.asList("null", "bla");
        objectAccessor.setObjectValue(objectToObjectConverter, list, "1", "blub");
        assertEquals(list.get(1),"blub");
    }

    @Test
    public void testSetValueOfDifferentTypeInGenericObjectListAtNullElement() throws Exception {
        List<Object> list = Arrays.asList(null, "bla");
        objectAccessor.setObjectValue(objectToObjectConverter, list, "0", new Date());
        assertTrue(list.get(0) instanceof Date);
    }

    @Test
    public void testSetValueOfDifferentTypeInGenericObjectList() throws Exception {
        List<Object> list = Arrays.asList("null", "bla");
        Date d = new Date();
        objectAccessor.setObjectValue(objectToObjectConverter, list, "1", d);
        assertTrue(list.get(1) instanceof String);
        // custom simplifier
        assertTrue(((String) list.get(1)).startsWith("UNIX-time:"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSetValueInvalidIndex() throws Exception {
        List<String> list = Arrays.asList("null", "bla");
        objectAccessor.setObjectValue(objectToObjectConverter, list, "NaN", "blub");
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSetValueIndexOutOfBounds() throws Exception {
        List<String> list = Arrays.asList("null", "bla");
        objectAccessor.setObjectValue(objectToObjectConverter, list, "3", "blub");
    }

    @Override
    ObjectAccessor createExtractor() {
        return new ListAccessor();
    }

}
