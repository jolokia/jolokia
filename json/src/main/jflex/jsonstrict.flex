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

import java.math.BigDecimal;
import java.math.BigInteger;

%%
// Options and declarations
// https://www.jflex.de/manual.html#options-and-declarations

%{
  private final StringBuilder sb;

  public int line() {
    return this.yyline + 1;
  }

  public int column() {
    return this.yycolumn + 1;
  }
%}

%unicode

%class Yylex
%ctorarg int capacity
%init{
  this.sb = new StringBuilder(capacity);
%init}

%yylexthrow ParseException

%line
%column
%char

%state STRING

// Macros

STRING_CHARACTER  = [^\"\\]
CONTROL_CHARACTER = [\u0000-\u001F]

HEX_DIGIT         = [a-fA-F0-9]
HEX_4DIGITS       = {HEX_DIGIT}{4}

// Numbers - https://datatracker.ietf.org/doc/html/rfc8259#section-6
// number = [ minus ] int [ frac ] [ exp ]
// There's explicit rule in JSON grammar `int = zero / ( digit1-9 *DIGIT )`, which means "00" is not allowed
// but Java would handle it correctly. ".3" is not allowed.
// Browser/JS `JSON.parse()` doesn't allow "00"
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt describes "n" suffix
// for numeric literals, but it's for JS BigInt values, not for JSON.
DIGIT19           = [1-9]
INT               = "0" | {DIGIT19}[0-9]*  // zero / ( digit1-9 *DIGIT )
FRAC              = \.[0-9]+               // decimal-point 1*DIGIT
EXP               = [eE][-+]?[0-9]+        // e [ minus / plus ] 1*DIGIT
// RFC8259 suggests that [-(2**53)+1, (2**53)-1] is an interoperable range for integers
// however at lexer stage, we only deal with BigDecimals and BigIntegers
INTEGER           = "-"?{INT}
DECIMAL           = "-"?{INT}{FRAC}?{EXP}?

// Insignificant whitespace is allowed before or after any of the six structural characters
WHITESPACE        = [ \t\n\r]

%%
// Lexical rules
// https://www.jflex.de/manual.html#LexRules

// based on:
//  - https://datatracker.ietf.org/doc/html/rfc8259#section-2 (JSON Grammar)
//  - https://github.com/fangyidong/json-simple/blob/master/doc/json.lex (handles Double and Long)
//  - https://github.com/cliftonlabs/json-simple/blob/master/src/main/lex/jsonstrict.lex (handles BigDecimals)

// rules to apply outside of string values
<YYINITIAL> {
  // start of string value - enter STRING STATE
  \"             { sb.setLength(0); yybegin(STRING); }

  {INTEGER}      { return new Yytoken(Yytoken.Kind.VALUE_INTEGER, new BigInteger(yytext())); }
  {DECIMAL}      { return new Yytoken(Yytoken.Kind.VALUE_DECIMAL, new BigDecimal(yytext())); }
  "true"|"false" { return new Yytoken(Yytoken.Kind.VALUE_BOOLEAN, "true".equals(yytext())); }
  "null"         { return new Yytoken(Yytoken.Kind.VALUE_NULL); }

  // These are the six structural characters
  "{"            { return new Yytoken(Yytoken.Kind.SYMBOL_LEFT_BRACE); }
  "}"            { return new Yytoken(Yytoken.Kind.SYMBOL_RIGHT_BRACE); }
  "["            { return new Yytoken(Yytoken.Kind.SYMBOL_LEFT_SQUARE); }
  "]"            { return new Yytoken(Yytoken.Kind.SYMBOL_RIGHT_SQUARE); }
  ","            { return new Yytoken(Yytoken.Kind.SYMBOL_COMMA); }
  ":"            { return new Yytoken(Yytoken.Kind.SYMBOL_COLON); }

  // Insignificant whitespace is allowed before or after any of the six structural characters
  {WHITESPACE}+  {}

  // any other character is an error
  .              { throw new ParseException(String.format("Invalid character '%s' at %d:%d", yytext(), yyline+1, yycolumn+1)); }
}

// rules to apply within string values
<STRING> {
  // end of string value - back to YYINITIAL state
  \"                  { yybegin(YYINITIAL); return new Yytoken(Yytoken.Kind.VALUE_STRING, sb.toString()); }

  // Escaping rules from:
  //  - https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-EscapeSequence
  //  - https://datatracker.ietf.org/doc/html/rfc8259#section-7

  // control characters (U+0000 through U+001F) MUST be escaped
  {CONTROL_CHARACTER} { throw new ParseException(String.format("Unescaped control character at %d:%d", yyline+1, yycolumn+1)); }

  // any character may be escaped
  \\u{HEX_4DIGITS}    {
    int hex = Integer.parseInt(yytext().substring(2), 16);
    sb.append((char) hex);
  }

  // quotation mark, reverse solidus (backslash) MUST be escaped
  \\\"                { sb.append('"'); }
  \\\\                { sb.append('\\'); }

  // alternatively, there are two-character sequence escape representations of some popular characters
  \\\/                { sb.append('/'); }
  \\b                 { sb.append('\b'); }
  \\f                 { sb.append('\f'); }
  \\n                 { sb.append('\n'); }
  \\r                 { sb.append('\r'); }
  \\t                 { sb.append('\t'); }

  // %x20-21 / %x23-5B / %x5D-10FFFF don't have to be escaped
  {STRING_CHARACTER}+ { sb.append(yytext()); }

  // fallback backslash which doesn't start known two-character escape sequence
  \\.                 { throw new ParseException(String.format("Invalid escape sequence '%s' at %d:%d", yytext(), yyline+1, yycolumn+1)); }

  // EOF within a string is parsing exception (JS' JSON.parse() does the same)
  <<EOF>>             { throw new ParseException(String.format("Unterminated string value at %d:%d", yyline+1, yycolumn+1)); }
}
