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

import org.jolokia.json.JSONArray;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ParserArraysTest {

    @Test
    public void unclosedArray() throws ParseException, IOException {
        try {
            new JSONParser().parse("[");
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Bad parser state, EOF at state PARSING_ARRAY");
        }
    }

    @Test
    public void unclosedArrayWithValue() throws ParseException, IOException {
        try {
            new JSONParser().parse("[42");
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Bad parser state, EOF at state PARSING_ARRAY");
        }
    }

    @Test
    public void leadingComma() throws IOException {
        try {
            new JSONParser().parse("[,");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Leading comma is not allowed within JSON array");
        }
    }

    @Test
    public void trailingComma() throws IOException {
        try {
            new JSONParser().parse("[42,]");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Trailing comma is not allowed within JSON array");
        }
    }

    @Test
    public void simplestArray() throws ParseException, IOException {
        JSONArray array = new JSONParser().parse("[]", JSONArray.class);
        assertNotNull(array);
        assertEquals(array.size(), 0);
    }

    @Test
    public void primitiveValues() throws ParseException, IOException {
        JSONArray array = new JSONParser().parse("["
            + "\"hello\","
            + "1.4,"
            + new BigInteger(Long.toString(Long.MAX_VALUE)).add(BigInteger.ONE).toString() + ","
            + new BigInteger(Long.toString(Long.MAX_VALUE)).subtract(BigInteger.ONE).toString() + ","
            + "42,"
            + "true,"
            + "null"
            + "]", JSONArray.class);
        assertNotNull(array);
        assertEquals(array.size(), 7);
        assertEquals(array.get(0), "hello");
        assertEquals(array.get(1), new BigDecimal("1.4"));
        assertEquals(array.get(2).toString(), "9223372036854775808");
        assertEquals(array.get(3), 9223372036854775806L);
        assertEquals(array.get(4), 42);
        assertTrue((Boolean) array.get(5));
        assertNull(array.get(6));
    }

}
