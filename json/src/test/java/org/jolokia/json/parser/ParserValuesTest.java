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

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ParserValuesTest {

    @Test
    public void parseEmptyString() {
        try {
            new JSONParser().parse(" ");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Can't parse any value from JSON stream");
            assertEquals(e.getLine(), 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void parseTwoStrings() {
        try {
            new JSONParser().parse("\n\"hello\"\n \"world\"");
            fail("Should have thrown an exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Multiple top-level values");
            assertEquals(e.getLine(), 3);
            assertEquals(e.getColumn(), 8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void parseSingleString() throws ParseException, IOException {
        Object value = new JSONParser().parse("\t\n\"\" ");
        assertTrue(value instanceof String);
        assertEquals(value, "");
    }

    @Test
    public void parseNull() throws ParseException, IOException {
        Object value = new JSONParser().parse(" null\t ");
        assertNull(value);
    }

    @Test
    public void parseBoolean() throws ParseException, IOException {
        Object value = new JSONParser().parse(" true\t ");
        assertTrue(value instanceof Boolean);
        assertTrue((Boolean) value);
    }

    @Test
    public void parseBigDecimal() throws ParseException, IOException {
        Object value = new JSONParser().parse(" -0.3e+4\t ");
        assertTrue(value instanceof BigDecimal);
        assertEquals(((BigDecimal) value).toPlainString(), "-3000");
    }

    @Test
    public void parseBigInteger() throws ParseException, IOException {
        Number value = (Number) new JSONParser().parse(new BigInteger(Long.toString(Long.MAX_VALUE)).add(BigInteger.TWO).toString());
        assertTrue(value instanceof BigInteger);
        assertEquals(value.toString(), "9223372036854775809");
    }

    @Test
    public void parseLong() throws ParseException, IOException {
        Number value = (Number) new JSONParser().parse(new BigInteger(Long.toString(Long.MAX_VALUE)).subtract(BigInteger.TWO).toString());
        assertTrue(value instanceof Long);
        assertEquals((long) value, 9223372036854775805L);
    }

    @Test
    public void parseInteger() throws ParseException, IOException {
        Number value = (Number) new JSONParser().parse(new BigInteger(Integer.toString(Integer.MAX_VALUE)).subtract(BigInteger.TWO).toString());
        assertTrue(value instanceof Long);
        assertEquals((long) value, 2147483645L);
    }

}
