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

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests related to a JSON lexer generated from JSON grammar using <a href="https://www.jflex.de/">JFlex</a>.
 * We're only interested in the tokens obtained from the {@link java.io.Reader}.
 */
public class LexerNumericValuesTest {

    @Test
    public void numericLimits() {
        System.out.printf("%d%n", Integer.MAX_VALUE);
        System.out.printf("%d%n", Long.MAX_VALUE);
        System.out.printf("0x%x%n", Integer.MAX_VALUE);
        System.out.printf("0x%x%n", Long.MAX_VALUE);

        System.out.println("MIN Long bits: " + new BigInteger(Long.toString(Long.MIN_VALUE)).bitLength());
        System.out.println("MAX Long bits: " + new BigInteger(Long.toString(Long.MAX_VALUE)).bitLength());
        System.out.println("MIN Long-1 bits: " + new BigInteger(Long.toString(Long.MIN_VALUE)).subtract(BigInteger.ONE).bitLength());
        System.out.println("MAX Long+1 bits: " + new BigInteger(Long.toString(Long.MAX_VALUE)).add(BigInteger.ONE).bitLength());

        System.out.println("MIN Integer bits: " + new BigInteger(Integer.toString(Integer.MIN_VALUE)).bitLength());
        System.out.println("MAX Integer bits: " + new BigInteger(Integer.toString(Integer.MAX_VALUE)).bitLength());
        System.out.println("MIN Integer-1 bits: " + new BigInteger(Integer.toString(Integer.MIN_VALUE)).subtract(BigInteger.ONE).bitLength());
        System.out.println("MAX Integer+1 bits: " + new BigInteger(Integer.toString(Integer.MAX_VALUE)).add(BigInteger.ONE).bitLength());
    }

    @Test
    public void exoticNumbers() {
        System.out.println(new BigDecimal("-0.0"));
        System.out.println(new BigDecimal("-0.0").equals(new BigDecimal("0.0")));
    }

    @Test
    public void basicTokenization() throws IOException {
        Yylex lexer = new Yylex(new StringReader("42 24"), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_INTEGER);
        assertEquals(token.getIntegerValue().longValue(), 42L);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_INTEGER);
        assertEquals(token.getIntegerValue().longValue(), 24L);
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

    @Test
    public void variousDecimalsAndIntegers() throws IOException {
        Yylex lexer = new Yylex(new StringReader("0 -0 0.0 -0.0 1 1.3 -2.4 1.0 1.0e3 -0.4e-1"), 16);
        assertNextIntToken(lexer, 0);
        assertNextIntToken(lexer, 0);
        assertNextDecimalToken(lexer, "0.0");
        assertNextDecimalToken(lexer, "0.0");
        assertNextIntToken(lexer, 1);
        assertNextDecimalToken(lexer, "1.3");
        assertNextDecimalToken(lexer, "-2.4");
        assertNextDecimalToken(lexer, "1.0");
        assertNextDecimalToken(lexer, "1000");
        assertNextDecimalToken(lexer, "-0.04");
        assertNull(lexer.yylex());
        assertTrue(lexer.yyatEOF());
    }

    private void assertNextIntToken(Yylex lexer, int value) throws IOException {
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_INTEGER);
        assertEquals(token.getIntegerValue().intValue(), value);
    }

    private void assertNextDecimalToken(Yylex lexer, String value) throws IOException {
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_DECIMAL);
        assertEquals(token.getDecimalValue().toPlainString(), new BigDecimal(value).toPlainString());
    }

}
