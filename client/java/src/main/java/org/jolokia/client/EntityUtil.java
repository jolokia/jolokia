/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;

import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;

/**
 * Helper class to deal with requests and responses regardless of the implementation of HTTP Client used.
 */
public class EntityUtil {

    /**
     * Helper method to transform a map of parameters to a query string of a HTTP request
     *
     * @param pProcessingOptions
     * @return
     */
    public static String toQueryString(Map<J4pQueryParameter, String> pProcessingOptions) {
        if (pProcessingOptions != null && !pProcessingOptions.isEmpty()) {
            StringBuilder queryParams = new StringBuilder();
            for (Map.Entry<J4pQueryParameter, String> entry : pProcessingOptions.entrySet()) {
                queryParams.append(entry.getKey().getParam()).append("=").append(entry.getValue()).append("&");
            }
            return queryParams.substring(0, queryParams.length() - 1);
        } else {
            return null;
        }
    }

    /**
     * Parse the JSON data available in the passed {@link InputStream}. It is the responsibility of the caller
     * to close this stream.
     *
     * @param body
     * @param charset
     * @return
     */
    public static JSONStructure parseJsonResponse(InputStream body, Charset charset) throws ParseException, IOException {
        JSONParser parser = new JSONParser();
        return (JSONStructure) parser.parse(new InputStreamReader(body, charset));
    }

}
