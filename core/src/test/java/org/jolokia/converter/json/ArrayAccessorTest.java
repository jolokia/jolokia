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

import javax.management.AttributeNotFoundException;
import javax.management.ObjectName;

import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONArray;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.09.11
 */
public class ArrayAccessorTest extends AbstractObjectAccessorTest {

    @Test
    public void simple() throws AttributeNotFoundException {
        JSONArray res = (JSONArray) extractJson(new String[]{"eins", "zwei", "drei"});
        assertEquals(res.size(),3);
        assertTrue(res.contains("eins"));
        assertTrue(res.contains("zwei"));
        assertTrue(res.contains("drei"));
    }

    @Test
    public void tooLong() throws AttributeNotFoundException {
        JSONArray res = (JSONArray) extractJson(new String[]{"eins", "zwei", "drei", "vier", "funf", "sechs"});
        assertEquals(res.size(), 5);
        assertTrue(res.contains("eins"));
        assertTrue(res.contains("zwei"));
        assertTrue(res.contains("drei"));
        assertTrue(res.contains("vier"));
        assertTrue(res.contains("funf"));
    }

    @Test
    public void simple2D() throws AttributeNotFoundException {
        JSONArray res = (JSONArray) extractJson(new String[][]{
            new String[]{"eins", "zwei", "drei"},
            new String[]{"jeden", "dwa", "trzy"},
        });
        assertEquals(res.size(),2);
        assertTrue(((JSONArray) res.get(0)).contains("eins"));
        assertTrue(((JSONArray) res.get(0)).contains("zwei"));
        assertTrue(((JSONArray) res.get(0)).contains("drei"));
        assertTrue(((JSONArray) res.get(1)).contains("jeden"));
        assertTrue(((JSONArray) res.get(1)).contains("dwa"));
        assertTrue(((JSONArray) res.get(1)).contains("trzy"));
    }

    @Test
    public void basics() {
        try {
            assertNull(objectAccessor.getType());
            fail();
        } catch (UnsupportedOperationException ignored) {
        }
        assertTrue(objectAccessor.canSetValue());
    }

    @Test
    public void withPath() throws AttributeNotFoundException {
        String res = (String) extractJson(new String[]{"eins", "zwei", "drei"},"1");
        assertEquals(res,"zwei");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithNonNumericPath() throws Exception {
        String res = (String) extractJson(new String[]{"eins", "zwei", "drei"},"blub");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testOutOfBoundsPath() throws Exception {
        String res = (String) extractJson(new String[]{"eins", "zwei", "drei"},"4");
    }

    @Test
    public void testWithWildcardPath() throws Exception {
        ObjectName[] names = {new ObjectName("test:type=blub"),null,new ObjectName("java.lang:type=Memory")};
        JSONArray result = (JSONArray) extractJson(names,null,"domain");
        assertEquals(result.size(),2);
        assertEquals(result.get(0),"test");
        assertEquals(result.get(1),"java.lang");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidWildcardPath() throws Exception {
        ObjectName[] names = {new ObjectName("test:type=blub"),null,new ObjectName("java.lang:type=Memory")};
        extractJson(names,null,"NotInMyHouse");
    }

    @Test
    public void direct() throws AttributeNotFoundException {
        int res = (Integer) extractObject(new Integer[]{1, 2, 3}, "2");
        assertEquals(res,3);
        Integer[] res2 = (Integer[]) extractObject(new Integer[]{1,2,3});
        assertEquals(res2.length,3);
        assertEquals((int) res2[0],1);
        assertEquals((int) res2[1],2);
        assertEquals((int) res2[2],3);
    }

    @Test
    public void setValue() throws InvocationTargetException, IllegalAccessException {
        Boolean[] inner = new Boolean[] { true, false, true };
        setObject(inner,"1",true);
        for (Boolean b : inner) {
            assertTrue(b);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*array.*")
    public void notAnArray() throws InvocationTargetException, IllegalAccessException {
        setObject("no array","1","blub");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*bla.*")
    public void notAnIndex() throws InvocationTargetException, IllegalAccessException {
        setObject(new Boolean[] { true, false, true },"bla",true);
    }

    @Override
    org.jolokia.converter.json.ObjectAccessor createExtractor() {
        return new ArrayAccessor();
    }

}
