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

/**
 * According to <a href="https://datatracker.ietf.org/doc/html/rfc8259#section-2">JSON Grammar</a>, there are two
 * structural elements in JSON - objects and arrays. This interface represents both of them.
 */
public interface JSONStructure {

    /**
     * Serialize given JSON structure to string. Can be used for debugging or small JSON data, but streaming
     * the JSON into {@link Writer} is preferred.
     * @return
     */
    String toJSONString();

    /**
     * Serialize this JSON structure to a {@link Writer}.
     * @param writer
     */
    void writeJSONString(Writer writer) throws IOException;

}
