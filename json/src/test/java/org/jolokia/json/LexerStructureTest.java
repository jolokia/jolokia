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

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests related to a JSON lexer generated from JSON grammar using <a href="https://www.jflex.de/">JFlex</a>.
 * We're only interested in the tokens obtained from the {@link java.io.Reader}.
 */
public class LexerStructureTest {

    @Test
    public void basicTokenization() throws IOException {
        Yylex lexer = new Yylex(new StringReader("{]"), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_LEFT_BRACE);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_RIGHT_SQUARE);
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

    @Test
    public void illegalCharacters() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"x\"x"), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "x");
        try {
            lexer.yylex();
            fail("Should have thrown exception");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Invalid character 'x' at 1:4");
        }
    }

    @Test
    public void literals() throws IOException {
        Yylex lexer = new Yylex(new StringReader("nullfalsetrue-1 -2.0e-0\"hello\"{}[]:,"), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_NULL);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_BOOLEAN);
        assertFalse(token.getBooleanValue());
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_BOOLEAN);
        assertTrue(token.getBooleanValue());
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_INTEGER);
        assertEquals(token.getIntegerValue().intValue(), -1);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_DECIMAL);
        assertEquals(token.getDecimalValue().toPlainString(), "-2.0");
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "hello");
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_LEFT_BRACE);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_RIGHT_BRACE);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_LEFT_SQUARE);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_RIGHT_SQUARE);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_COLON);
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.SYMBOL_COMMA);
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

}
