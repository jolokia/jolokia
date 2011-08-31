package org.jolokia.http;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.servlet.ServletInputStream;

/**
 * @author roland
 * @since 31.08.11
 */
class HttpTestUtil {

    public static final String HEAP_MEMORY_POST =
            "{ \"type\": \"read\",\"mbean\": \"java.lang:type=Memory\", \"attribute\": \"HeapMemoryUsage\"}";
    public static final String HEAP_MEMORY_GET = "/read/java.lang:type=Memory/HeapMemoryUsage";

    static ServletInputStream createServletInputStream(String pReq) {
        final ByteArrayInputStream bis =
                new ByteArrayInputStream(pReq.getBytes());
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return bis.read();
            }
        };
    }
}
