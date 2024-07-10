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
public class LexerStringValuesTest {

    @Test
    public void basicTokenization() throws IOException {
        // https://www.jflex.de/manual.html#ScanningMethod
        Yylex lexer = new Yylex(new StringReader("\"Hello world!\"\"token2\" \"token3\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "Hello world!");
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "token2");
        token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "token3");
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

    @Test
    public void escapingStringCharactersWithoutUnicode() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "\" \\ / \b \f \n \r \t");
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

    @Test()
    public void nonEscapedBackslashAtTheEndOfString() throws IOException {
        // In browser:
        //      JSON.parse("\"\\\"")
        // fails with: Uncaught SyntaxError: JSON.parse: unterminated string at line 1 column 4 of the JSON data
        Yylex lexer = new Yylex(new StringReader("\"\\\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = null;
        try {
            lexer.yylex();
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Unterminated string value at 1:4");
        }
    }

    @Test()
    public void escapedBackslashAtTheEndOfString() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\\\\\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(), "\\");
        assertEquals(token.getStringValue().length(), 1);
        assertEquals(token.getStringValue().charAt(0), '\\');
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

    @Test()
    public void emptyString() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(),"");
        assertEquals(token.getStringValue().length(), 0);
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

    @Test()
    public void nonEscapedBackslash() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\\t\\u\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = null;
        try {
            lexer.yylex();
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Invalid escape sequence '\\u' at 1:4");
        }
    }

    @Test()
    public void nonEscapedControlCharacter() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\b\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = null;
        try {
            lexer.yylex();
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Unescaped control character at 1:2");
        }
    }

    @Test()
    public void nonEscapedUnicodeControlCharacter() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\u001f\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = null;
        try {
            lexer.yylex();
            fail("Should have thrown ParseException");
        } catch (ParseException e) {
            assertEquals(e.getMessage(), "Unescaped control character at 1:2");
        }
    }

    @Test()
    public void escapedUnicodeControlCharacter() throws IOException {
        Yylex lexer = new Yylex(new StringReader("\"\\u001f\""), 16);
        assertFalse(lexer.yyatEOF());
        Yytoken token = lexer.yylex();
        assertNotNull(token);
        assertEquals(token.getKind(), Yytoken.Kind.VALUE_STRING);
        assertEquals(token.getStringValue(),"\u001F");
        assertEquals(token.getStringValue().length(), 1);
        token = lexer.yylex();
        assertNull(token);
        assertTrue(lexer.yyatEOF());
    }

}
