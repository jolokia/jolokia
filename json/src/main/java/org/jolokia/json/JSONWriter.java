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
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * Jolokia JSON serialization support
 */
public class JSONWriter {

    /**
     * Serialize a string-keyed map (including {@link JSONObject}) into a {@link Writer}
     * @param map
     * @param writer
     * @throws IOException
     */
    public static void serialize(Map<String, Object> map, Writer writer) throws IOException {
        writer.write('{');
        int pos = map.size() - 1;
        for (Map.Entry<String, Object> el : map.entrySet()) {
            escape(writer, el.getKey().toCharArray());
            writer.write(':');
            serialize(el.getValue(), writer);
            if (pos-- > 0) {
                writer.write(',');
            }
        }
        writer.write('}');
    }

    /**
     * Serialize any map into a {@link Writer}
     * @param map
     * @param writer
     * @throws IOException
     */
    public static void serializeAnyMap(Map<?, ?> map, Writer writer) throws IOException {
        writer.write('{');
        int pos = map.size() - 1;
        for (Map.Entry<?, ?> el : map.entrySet()) {
            serialize(el.getKey() == null ? "" : el.getKey().toString(), writer);
            writer.write(':');
            serialize(el.getValue(), writer);
            if (pos-- > 0) {
                writer.write(',');
            }
        }
        writer.write('}');
    }

    /**
     * Serialize a collection (including {@link JSONArray}) into a {@link Writer}
     * @param collection
     * @param writer
     * @throws IOException
     */
    public static void serialize(Collection<?> collection, Writer writer) throws IOException {
        writer.write('[');
        int pos = collection.size() - 1;
        for (Object el : collection) {
            serialize(el, writer);
            if (pos-- > 0) {
                writer.write(',');
            }
        }
        writer.write(']');
    }

    /**
     * Serialize generic object as JSON into {@link Writer}
     * @param value
     * @param writer
     * @throws IOException
     */
    public static void serialize(Object value, Writer writer) throws IOException {
        if (value == null) {
            writer.write("null");
        } else if (value instanceof Boolean) {
            writer.write((boolean) value ? "true" : "false");
        } else if (value instanceof Float) {
            if (Float.isFinite((float) value)) {
                writer.write(value.toString());
            } else {
                writer.write("null");
            }
        } else if (value instanceof Double) {
            if (Double.isFinite((double) value)) {
                writer.write(value.toString());
            } else {
                writer.write("null");
            }
        } else if (value instanceof Number) {
            // includes BigDecimals and BigIntegers
            writer.write(value.toString());
        } else if (value instanceof Character) {
            escape(writer, new char[] { (char) value });
        } else if (value instanceof String) {
            escape(writer, ((String) value).toCharArray());
        } else if (value instanceof Collection<?> collection) {
            serialize(collection, writer);
        } else if (value instanceof JSONObject) {
            serialize((JSONObject) value, writer);
        } else if (value instanceof Map<?, ?> map) {
            // not sure about the key types, so be extra careful
            serializeAnyMap(map, writer);
        } else if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            writer.write('[');
            for (int i = 0; i < size; i++) {
                serialize(Array.get(value, i), writer);
                if (i < size - 1) {
                    writer.write(',');
                }
            }
            writer.write(']');
        }
    }

    /**
     * When writing string values we have to escape characters. This method writes escaped char array into
     * the target {@link Writer} but optimizing the process by not writing it one char at a time. Also
     * the char array is surrounded by quotes.
     * @param characters
     * @param writer
     * @throws IOException
     */
    private static void escape(Writer writer, char[] characters) throws IOException {
        // https://datatracker.ietf.org/doc/html/rfc8259#section-7
        //     All Unicode characters may be placed within the
        //     quotation marks, except for the characters that MUST be escaped:
        //     quotation mark, reverse solidus, and the control characters (U+0000
        //     through U+001F).
        //
        //     Any character may be escaped.  If the character is in the Basic
        //     Multilingual Plane (U+0000 through U+FFFF), then it may be
        //     represented as a six-character sequence: a reverse solidus, followed
        //     by the lowercase letter u, followed by four hexadecimal digits that
        //     encode the character's code point
        //
        //     Alternatively, there are two-character sequence escape
        //     representations of some popular characters
        //
        //     unescaped = %x20-21 / %x23-5B / %x5D-10FFFF
        //
        // single "char" which java.lang.Character.isSurrogate() is passed directly, to be decoded by parser
        // when needed

        StringBuilder buffer = new StringBuilder();

        buffer.append('"');
        for (char c : characters) {
            switch (c) {
                case '"': // %x22
                    buffer.append("\\\"");
                    break;
                case '\\': // %x5C
                    buffer.append("\\\\");
                    break;
                // RFC 8259 says that "/" may be escaped and we unescape it when parsing `\/`. But we don't escape
                // it during serialization
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("\\n");
                    break;
                case '\r':
                    buffer.append("\\r");
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                default:
                    if (c <= 0x1F) {
                        buffer.append("\\u00");
                        buffer.append((char)(((c & 0xf0) >> 4) + '0'));
                        buffer.append((char)((c & 0x0f) + '0'));
                    } else {
                        // there's no escape
                        buffer.append(c);
                    }
                    break;
            }
        }
        buffer.append('"');

        writer.write(buffer.toString());
    }

}
