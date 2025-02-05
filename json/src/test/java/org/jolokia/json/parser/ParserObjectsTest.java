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
package org.jolokia.json.parser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ParserObjectsTest {

    @Test
    public void unclosedObject() throws ParseException, IOException {
        try {
            new JSONParser().parse("{");
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Bad parser state, EOF at state PARSING_OBJECT");
        }
    }

    @Test
    public void unclosedObjectWithValue() throws ParseException, IOException {
        try {
            new JSONParser().parse("{\"key\":\"value\"");
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Bad parser state, EOF at state PARSING_OBJECT");
        }
    }

    @Test
    public void leadingComma() throws IOException {
        try {
            new JSONParser().parse("{,");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Leading comma is not allowed within JSON object");
        }
    }

    @Test
    public void trailingComma() throws IOException {
        try {
            new JSONParser().parse("{\"key\":\"value\",}");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Trailing comma is not allowed within JSON object");
        }
    }

    @Test
    public void nonStringKey() throws IOException {
        try {
            new JSONParser().parse("{42,");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Only string keys are allowed within JSON object");
        }
    }

    @Test
    public void eofAfterStringKey() throws IOException {
        try {
            new JSONParser().parse("{\"42\"");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Expected ':' after key");
        }
    }

    @Test
    public void noColonAfterStringKey() throws IOException {
        try {
            new JSONParser().parse("{\"42\"1");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Expected ':' after key");
        }
    }

    @Test
    public void simplestObject() throws ParseException, IOException {
        JSONObject object = new JSONParser().parse("{}", JSONObject.class);
        assertNotNull(object);
        assertEquals(object.size(), 0);
    }

    @Test
    public void primitiveKeys() throws ParseException, IOException {
        JSONObject object = new JSONParser().parse("{"
            + "\"keyString\":\"hello\","
            + "\"keyBigDecimal\":1.4,"
            + "\"keyBigInteger\":" + new BigInteger(Long.toString(Long.MAX_VALUE)).add(BigInteger.ONE).toString() + ","
            + "\"keyLong\":" + new BigInteger(Long.toString(Long.MAX_VALUE)).subtract(BigInteger.ONE).toString() + ","
            + "\"keyInteger\":42,"
            + "\"keyBoolean\":true,"
            + "\"keyNull\":null"
            + "}", JSONObject.class);
        assertNotNull(object);
        assertEquals(object.size(), 7);
        assertEquals(object.get("keyString"), "hello");
        assertEquals(object.get("keyBigDecimal"), new BigDecimal("1.4"));
        assertEquals(object.get("keyBigInteger").toString(), "9223372036854775808");
        assertEquals(object.get("keyLong"), 9223372036854775806L);
        assertEquals(object.get("keyInteger"), 42L);
        assertTrue((Boolean) object.get("keyBoolean"));
        assertTrue(object.containsKey("keyNull"));
        assertNull(object.get("keyNull"));
    }

}
