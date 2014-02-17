package org.jolokia.test.util;

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
import java.util.*;

import javax.servlet.*;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

/**
 * Test utility methods for HTTP related tests
 *
 * @author roland
 * @since 31.08.11
 */
public class HttpTestUtil {

    /**
     * POST JSON sample request in string form
     */
    public static final String HEAP_MEMORY_POST_REQUEST =
            "{ \"type\": \"read\",\"mbean\": \"java.lang:type=Memory\", \"attribute\": \"HeapMemoryUsage\"}";

    /**
     * GET sample request in string form
     */
    public static final String HEAP_MEMORY_GET_REQUEST = "/read/java.lang:type=Memory/HeapMemoryUsage";

    private HttpTestUtil() { }

    /**
     * Create a servlet input stream usable in tests
     *
     * @param pData data which should be returned on read of the stream
     * @return the created servlet input stream
     */
    public static ServletInputStream createServletInputStream(String pData) {
        final ByteArrayInputStream bis =
                new ByteArrayInputStream(pData.getBytes());
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return bis.read();
            }
        };
    }

    /**
     * Prepare a servlet config Mock
     *
     * @param config configuration mock to prepare
     * @param pInitParams init params to return on config.getInitParameter()
     */
    @SuppressWarnings("PMD.ReplaceVectorWithList")
    public static void prepareServletConfigMock(ServletConfig config,String ... pInitParams) {
        Map<String,String> configParams = new HashMap<String, String>();
        if (pInitParams != null) {
            for (int i = 0; i < pInitParams.length; i += 2) {
                configParams.put(pInitParams[i],pInitParams[i+1]);
            }
            for (Map.Entry<String,String> entry : configParams.entrySet()) {
                EasyMock.expect(config.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
            }
        }

        final Vector paramNames = new Vector(configParams.keySet());
        EasyMock.expect(config.getInitParameterNames()).andAnswer(new IAnswer<Enumeration>() {
            public Enumeration answer() throws Throwable {
                return paramNames.elements();
            }
        }).anyTimes();
    }

    /**
     * Prepare a servlet context Mock so that the config parameters are returned properly
     *
     * @param pContext mocked context
     * @param pContextParams context parameters to return
     */
    public static void prepareServletContextMock(ServletContext pContext, String ... pContextParams) {
        Map<String,String> configParams = new HashMap<String, String>();
        if (pContextParams != null) {
            for (int i = 0; i < pContextParams.length; i += 2) {
                configParams.put(pContextParams[i],pContextParams[i+1]);
            }
            for (Map.Entry<String,String> entry : configParams.entrySet()) {
                EasyMock.expect(pContext.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
            }
        }
        final Vector paramNames = new Vector(configParams.keySet());
        EasyMock.expect(pContext.getInitParameterNames()).andAnswer(new IAnswer<Enumeration>() {
            public Enumeration answer() throws Throwable {
                return paramNames.elements();
            }
        }).anyTimes();
    }
}
