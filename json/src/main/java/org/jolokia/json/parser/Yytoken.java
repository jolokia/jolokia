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

public class Yytoken {

    /**
     * <p>Token types according to
     * <a href="https://datatracker.ietf.org/doc/html/rfc8259#section-3">JSON Grammar</a>.</p>
     *
     * <p>The set of tokens includes six structural characters, strings, numbers, and three literal names</p>
     *
     * <p>These are the six structural characters: left square bracket, left curly bracket, right square bracket,
     * right curly bracket, colon, comma.</p>
     *
     * <p>A JSON value MUST be an object, array, number, or string, or one of the following three literal
     * names: {@code true}, {@code false}, {@code null}.</p>
     */
    public enum Kind {
        VALUE_INTEGER,
        VALUE_DECIMAL,
        VALUE_STRING,
        VALUE_BOOLEAN,
        VALUE_NULL,
        SYMBOL_LEFT_BRACE,
        SYMBOL_RIGHT_BRACE,
        SYMBOL_LEFT_SQUARE,
        SYMBOL_RIGHT_SQUARE,
        SYMBOL_COMMA,
        SYMBOL_COLON,
    }

    private final Kind kind;

    private boolean booleanValue;
    private String stringValue;
    private Number numberValue;

    public Yytoken(Kind kind) {
        this.kind = kind;
    }

    public Yytoken(Kind kind, String value) {
        this.kind = kind;
        this.stringValue = value;
    }

    public Yytoken(Kind kind, BigInteger value) {
        this.kind = kind;
        this.numberValue = value;
    }

    public Yytoken(Kind kind, BigDecimal value) {
        this.kind = kind;
        this.numberValue = value;
    }

    public Yytoken(Kind kind, boolean value) {
        this.kind = kind;
        this.booleanValue = value;
    }

    public Kind getKind() {
        return kind;
    }

    public String getStringValue() {
        return stringValue;
    }

    public BigInteger getIntegerValue() {
        return kind == Kind.VALUE_INTEGER ? (BigInteger) numberValue : null;
    }

    public BigDecimal getDecimalValue() {
        return kind == Kind.VALUE_DECIMAL ? (BigDecimal) numberValue : null;
    }

    public boolean getBooleanValue() {
        return booleanValue;
    }

    @Override
    public String toString() {
        switch (kind) {
            case VALUE_STRING:
                return "{" + kind + ": [" + stringValue + "]}";
            case VALUE_INTEGER:
            case VALUE_DECIMAL:
                return "{" + kind + ": [" + numberValue + "]}";
            default:
                return "{unknown}";
        }
    }

}
