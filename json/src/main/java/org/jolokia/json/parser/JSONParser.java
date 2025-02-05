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
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;

/**
 * Jolokia JSON parser using {@link Yylex} lexer generated from JSON grammar using
 * <a href="https://www.jflex.de/">JFlex</a>.
 */
public class JSONParser {

    private enum State {
        /** State at the start of parsing. Expects single top-level value */
        INITIAL,
        /** State after getting single top-level value. More tokens is a sign of error */
        DONE,
        /** After entering object (after left brace) */
        PARSING_OBJECT,
        /** After entering array (after left square bracket) */
        PARSING_ARRAY,
        /** In object, after reading key or in array */
        PARSING_VALUE,
        /** Extra state to detect trailing commas - disallowed by RFC 8259 */
        COMMA
    }

    // stack of parser states
    private final Deque<State> states = new ArrayDeque<>(256);
    // stack of parser values
    private final Deque<Object> values = new ArrayDeque<>(256);

    // null marker, because ArrayDeque doesn't allow real nulls
    private final Object NULL = new Object();

    /**
     * Main <em>parse</em> method that extract {@link JSONStructure} or primitive value (string, number,
     * boolean or null) from JSON stream.
     *
     * @param reader
     * @return
     */
    public Object parse(Reader reader) throws ParseException, IOException, IllegalStateException {
        Yylex lexer = new Yylex(reader, 16);

        states.push(State.INITIAL);

        // state based parsing of JSON tokens
        while (!lexer.yyatEOF()) {
            Yytoken token = lexer.yylex();
            if (token == null) {
                break;
            }
            int line = lexer.line();
            int column = lexer.column();

            if (states.isEmpty()) {
                throw new IllegalStateException("Remaining JSON data at (" + line + ":" + column + ")");
            }

            switch (states.peek()) {
                case INITIAL:
                    // expect single top level value (structure or primitive)
                    processValue(lexer, token, true);
                    break;
                case DONE:
                    // no more tokens expected, so this state is not expected at all
                    handleNotCleanEndState(lexer);
                    break;
                case PARSING_OBJECT:
                    // expect key or closing '}'. After getting a key, we'll wait for the value
                    processObject(lexer, token, false);
                    break;
                case PARSING_ARRAY:
                    // expect value (including new array or new object), comma or closing ']'
                    processArray(lexer, token, false);
                    break;
                case COMMA:
                    // lower state is either PARSING_OBJECT or PARSING_ARRAY, but related object/array can't end yet
                    processComma(lexer, token);
                    break;
                case PARSING_VALUE:
                    // for objects, ":" is already swallowed
                    processValue(lexer, token, false);
                    break;
                default:
                    break;
            }
        }

        if (values.isEmpty()) {
            throw new ParseException("Can't parse any value from JSON stream")
                .at(lexer.line(), lexer.column());
        }

        if (states.isEmpty()) {
            throw new IllegalStateException("Bad parser state (not DONE, EOF encountered)");
        }

        if (states.peek() != State.DONE) {
            throw new IllegalStateException("Bad parser state, EOF at state " + states.peek());
        }

        Object value = values.pop();
        return value == NULL ? null : value;
    }

    /**
     * Reading value - single top-level value, value for a key of an object or value within array.
     *
     * @param lexer
     * @param token
     * @param topLevel
     */
    private void processValue(Yylex lexer, Yytoken token, boolean topLevel) throws ParseException {
        Object primitiveValue = null;

        switch (token.getKind()) {
            case SYMBOL_LEFT_BRACE:
                states.push(State.PARSING_OBJECT);
                values.push(new JSONObject());
                break;
            case SYMBOL_LEFT_SQUARE:
                states.push(State.PARSING_ARRAY);
                values.push(new JSONArray());
                break;
            case VALUE_STRING:
                primitiveValue = token.getStringValue();
                break;
            case VALUE_INTEGER:
                primitiveValue = optimizedIntegerValue(token.getIntegerValue());
                break;
            case VALUE_DECIMAL:
                // never optimize to Double or Float
                primitiveValue = token.getDecimalValue();
                break;
            case VALUE_BOOLEAN:
                primitiveValue = token.getBooleanValue();
                break;
            case VALUE_NULL:
                primitiveValue = NULL;
                break;
            default:
                if (topLevel) {
                    throw new ParseException("Unexpected top-level token (kind: " + token.getKind().name() + ")")
                        .at(lexer.line(), lexer.column());
                } else {
                    throw new ParseException("Unexpected token (kind: " + token.getKind().name() + ")")
                        .at(lexer.line(), lexer.column());
                }
        }

        // primitive values are completed
        // objects/arrays on stack will be handled after their closing "}" / "]"
        if (token.getKind().isValueToken()) {
            valueReady(lexer, primitiveValue);
        }
    }

    /**
     * Process the state where we're inside an object and the top value on the stack is
     * {@link JSONObject}. We've read the opening "{" and we expect keys, commas and closing "}". Values are
     * handled in separate parser state.
     * @param lexer
     * @param token
     * @param hadComma
     * @throws ParseException
     */
    private void processObject(Yylex lexer, Yytoken token, boolean hadComma) throws ParseException, IOException {
        Yytoken.Kind kind = token.getKind();
        Object currentValue = values.peek();
        if (currentValue == null || currentValue.getClass() != JSONObject.class) {
            throw new IllegalStateException("Parser expects JSONObject as current value (" + lexer.line() + ":" + lexer.column() + ")");
        }

        if (kind == Yytoken.Kind.SYMBOL_RIGHT_BRACE) {
            if (hadComma) {
                throw new ParseException("Trailing comma is not allowed within JSON object")
                    .at(lexer.line(), lexer.column());
            }
            states.pop();
            // keep the current value on stack (should be completed JSONObject)
            valueReady(lexer, null);
            return;
        }

        if (kind == Yytoken.Kind.SYMBOL_COMMA) {
            if (((JSONObject) currentValue).isEmpty()) {
                // current object can't be empty - we don't allow "{,..."
                throw new ParseException("Leading comma is not allowed within JSON object")
                    .at(lexer.line(), lexer.column());
            }
            // push COMMA state to detect trailing commas, keep the current value on stack (should be JSONObject)
            states.push(State.COMMA);
            return;
        }

        // expect string key
        if (kind.isValueToken()) {
            if (kind != Yytoken.Kind.VALUE_STRING) {
                throw new ParseException("Only string keys are allowed within JSON object")
                    .at(lexer.line(), lexer.column());
            }
            // immediately swallow ":"
            Yytoken expectedColon = lexer.yylex();
            if (expectedColon == null || expectedColon.getKind() != Yytoken.Kind.SYMBOL_COLON) {
                throw new ParseException("Expected ':' after key")
                    .at(lexer.line(), lexer.column());
            }
            // push the key
            values.push(token.getStringValue());
            // expect next token to be/start a value for the key
            states.push(State.PARSING_VALUE);

            return;
        }

        throw new ParseException("Unexpected token (kind: " + token.getKind() + ") when parsing JSON object")
            .at(lexer.line(), lexer.column());
    }

    /**
     * Process the state where we're inside an array and the top value on the stack is
     * {@link JSONArray}. We're past the opening "[" and we process values and commas.
     * @param lexer
     * @param token
     * @param hadComma
     * @throws ParseException
     */
    private void processArray(Yylex lexer, Yytoken token, boolean hadComma) throws ParseException {
        Yytoken.Kind kind = token.getKind();
        Object currentValue = values.peek();
        if (currentValue == null || currentValue.getClass() != JSONArray.class) {
            throw new IllegalStateException("Parser expects JSONArray as current value (" + lexer.line() + ":" + lexer.column() + ")");
        }

        if (kind == Yytoken.Kind.SYMBOL_RIGHT_SQUARE) {
            if (hadComma) {
                throw new ParseException("Trailing comma is not allowed within JSON array")
                    .at(lexer.line(), lexer.column());
            }
            states.pop();
            // keep the current value on stack (should be completed JSONArray)
            valueReady(lexer, null);
            return;
        }

        if (kind == Yytoken.Kind.SYMBOL_COMMA) {
            if (((JSONArray) currentValue).isEmpty()) {
                // current array can't be empty - we don't allow "[,..."
                throw new ParseException("Leading comma is not allowed within JSON array")
                    .at(lexer.line(), lexer.column());
            }
            // push COMMA state to detect trailing commas, keep the current value on stack (should be JSONArray)
            states.push(State.COMMA);
            return;
        }

        // any other token is a value of the array - just process it without touching the lexer
        processValue(lexer, token , false);
    }

    /**
     * Parser in {@link State#COMMA} state is actually in object/array parsing state, but with additional
     * restriction - object/array can't end.
     * @param lexer
     */
    private void processComma(Yylex lexer, Yytoken token) throws ParseException, IOException {
        states.pop();
        if (states.isEmpty()) {
            throw new IllegalStateException("Bad parser state, expected PARSING_OBJECT or PARSING_ARRAY");
        }

        State state = states.peek();
        if (state == State.PARSING_ARRAY) {
            processArray(lexer, token, true);
        } else if (state == State.PARSING_OBJECT) {
            processObject(lexer, token, true);
        }
    }

    /**
     * Parser state method called when we have value ready which is not top-level value
     * @param value completed primitive value (to optimize stack usage). When completing structure, we'll find
     *              it on the stack and this parameter is {@code null}.
     */
    private void valueReady(Yylex lexer, Object value) {
        State state = states.peek();
        if (state == null) {
            throw new IllegalStateException("Bad parser state when handling value at ("
                + lexer.line() + ":" + lexer.column() + ")");
        }

        int requiredStackSize = 0;
        switch (state) {
            case INITIAL:
                if (value == null) {
                    requiredStackSize = 1;
                }
                break;
            case PARSING_ARRAY:
                // we need value and the array
                requiredStackSize = value == null ? 2 : 1;
                break;
            case PARSING_OBJECT:
                // we need value, key and the object
                requiredStackSize = value == null ? 3 : 2;
                break;
        }

        if (values.size() < requiredStackSize) {
            if (state == State.PARSING_OBJECT) {
                throw new IllegalStateException("Can't process value for JSON object. Bad stack size (size: " + values.size() + ")");
            }
            if (state == State.PARSING_ARRAY) {
                throw new IllegalStateException("Can't process value for JSON array. Bad stack size (size: " + values.size() + ")");
            }
        }

        if (value == null) {
            // we need it from the stack
            value = values.pop();
        }

        switch (state) {
            case INITIAL:
                // simple - we have ready, top-level value
                values.push(value);
                // more explicit than popping state, expecting INITIAL and handling top value from the stack.
                // also we can detect multi-root values
                states.push(State.DONE);
                break;
            case PARSING_VALUE: {
                Object key = values.pop();
                Object object = values.peek();
                if (!(object instanceof JSONObject)) {
                    throw new IllegalStateException("Can't process value for JSON object. Wrong object (got: "
                        + (object == null ? "<null>" : object.getClass()) + ")");
                }
                if (!(key instanceof String)) {
                    throw new IllegalStateException("Can't process value for JSON object. Wrong key type (got: "
                        + (key == null ? "<null>" : key.getClass()) + ")");
                }
                ((JSONObject) object).put((String) key, value == NULL ? null : value);
                // back to parsing object - more entries (until '}')
                states.pop();
                break;
            }
            case PARSING_ARRAY: {
                Object array = values.peek();
                if (!(array instanceof JSONArray)) {
                    throw new IllegalStateException("Can't process value for JSON array. Wrong object (got: "
                        + (array == null ? "<null>" : array.getClass()) + ")");
                }
                ((JSONArray) array).add(value == NULL ? null : value);
                // no state change (until closing ']')
                break;
            }
            default:
                break;
        }
    }

    /**
     * Called after handling single top-level value in situation where lexer is not at EOF.
     * @param lexer
     * @throws ParseException
     */
    private void handleNotCleanEndState(Yylex lexer) throws ParseException {
        states.pop();
        State state = states.pop();
        if (state != State.INITIAL) {
            throw new IllegalStateException("Expected top-level state (got: " + state + ") at ("
                + lexer.line() + ":" + lexer.column() + ")");
        }
        throw new ParseException("Multiple top-level values").at(lexer.line(), lexer.column());
    }

    /**
     * If we can fit {@link BigInteger} in smaller object, we'll do it
     * @param v
     * @return
     */
    private Number optimizedIntegerValue(BigInteger v) {
//        if (v.bitLength() <= 7) {
//            return v.byteValue();
//        }
//        if (v.bitLength() <= 15) {
//            return v.shortValue();
//        }
//        if (v.bitLength() <= 31) {
//            return v.intValue();
//        }
        if (v.bitLength() <= 63) {
            return v.longValue();
        }
        return v;
    }

    /**
     * Parse JSON data expecting specific object type.
     *
     * @param reader
     * @param clazz
     * @return
     * @param <T>
     */
    public <T> T parse(Reader reader, Class<T> clazz) throws ParseException, IOException {
        Object result = parse(reader);
        if (clazz.isInstance(result)) {
            return clazz.cast(result);
        }
        throw new IllegalArgumentException("Can't parse JSON data into " + clazz.getName() + " (got " + result.getClass() + ")");
    }

    /**
     * Parse direct string value containing JSON data.
     *
     * @param json
     * @return
     */
    public Object parse(String json) throws ParseException, IOException {
        return parse(new StringReader(json));
    }

    /**
     * Parse JSON string expecting specific object type.
     *
     * @param json
     * @param clazz
     * @return
     * @param <T>
     */
    public <T> T parse(String json, Class<T> clazz) throws ParseException, IOException {
        return parse(new StringReader(json), clazz);
    }

}
