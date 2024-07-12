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
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

/**
 * <a href="https://datatracker.ietf.org/doc/html/rfc8259#section-5">JSON Array</a>.
 */
public class JSONArray extends ArrayList<Object> implements JSONStructure {

    public JSONArray() {
        super(256);
    }

    public JSONArray(Collection<?> c) {
        super(c);
    }

    @Override
    public String toJSONString() {
        StringWriter writer = new StringWriter();
        try {
            writeJSONString(writer);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return writer.toString();
    }

    @Override
    public void writeJSONString(Writer writer) throws IOException {
        JSONWriter.serialize(this, writer);
    }

    @Override
    public String toString() {
        return super.toString();
    }

}
