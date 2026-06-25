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
            escape(writer, el.getKey());
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
        } else if (value instanceof Boolean b) {
            writer.write(b ? "true" : "false");
        } else if (value instanceof Number num) {
            if (num instanceof Double d) {
                if (Double.isFinite(d)) {
                    writer.write(d.toString());
                } else {
                    writer.write("null");
                }
            } else if (num instanceof Float f) {
                if (Float.isFinite(f)) {
                    writer.write(f.toString());
                } else {
                    writer.write("null");
                }
            } else {
                writer.write(num.toString());
            }
        } else if (value instanceof Character c) {
            escape(writer, c);
        } else if (value instanceof String s) {
            escape(writer, s);
        } else if (value instanceof Collection<?> collection) {
            serialize(collection, writer);
        } else if (value instanceof JSONObject j) {
            serialize(j, writer);
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
     * When writing string values we have to escape characters. This method uses a sliding-window
     * approach to track contiguous blocks of unescaped characters.
     * The output is enclosed in double quotes.
     * @param characters
     * @param writer
     * @throws IOException
     */

    private static void escape(Writer writer, String str) throws IOException {
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

        writer.write('"');

        int length = str.length();
        int runStart = 0;

        for (int i = 0; i < length; i++) {
            char c = str.charAt(i);
            
            if (c > 0x1F && c != '"' && c != '\\') {
                continue;
            }

            if (i > runStart) {
                writer.write(str, runStart, i - runStart);
            }

            writeEscape(writer, c);
            runStart = i + 1;
        }

        if (runStart < length) {
            writer.write(str, runStart, length - runStart);
        }

        writer.write('"');
    }

    private static void escape(Writer writer, char c) throws IOException {
        writer.write('"');
        if (c > 0x1F && c != '"' && c != '\\') {
            writer.write(c);
        } else {
            writeEscape(writer, c);
        }
        writer.write('"');
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static void writeEscape(Writer writer, char c) throws IOException {
        switch (c) {
            case '"':  writer.write("\\\""); break; // %x22
            case '\\': writer.write("\\\\"); break; // %x5C
            // RFC 8259 says that "/" may be escaped and we unescape it when parsing `\/`. But we don't escape
            // it during serialization
            case '\b': writer.write("\\b"); break;
            case '\f': writer.write("\\f"); break;
            case '\n': writer.write("\\n"); break;
            case '\r': writer.write("\\r"); break;
            case '\t': writer.write("\\t"); break;
            default:
                writer.write("\\u00");
                writer.write(HEX_CHARS[(c >>> 4) & 0X0F]);
                writer.write(HEX_CHARS[c & 0X0F]);
                break;
        }
    }

}
