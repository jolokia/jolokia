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

import java.io.Reader;
import java.io.StringReader;

/**
 * Jolokia JSON parser using {@link Yylex} lexer generated from JSON grammar using
 * <a href="https://www.jflex.de/">JFlex</a>.
 */
public class JSONParser {

    /**
     * Main parse method that extract {@link org.jolokia.json.JSONStructure} or primitive value (string, number,
     * boolean or null) from JSON stream.
     *
     * @param reader
     * @return
     */
    public Object parse(Reader reader) {
        return null;
    }

    /**
     * Parse direct string value containing JSON data.
     *
     * @param json
     * @return
     */
    public Object parse(String json) {
        return parse(new StringReader(json));
    }

}
