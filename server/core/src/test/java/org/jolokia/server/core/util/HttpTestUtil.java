package org.jolokia.server.core.util;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;

import org.easymock.EasyMock;

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
    public static final String VERSION_POST_REQUEST =
            "{ \"type\": \"version\"}";

    /**
     * GET sample request in string form
     */
    public static final String VERSION_GET_REQUEST = "/version";

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
            public int read() {
                return bis.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
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
        Map<String,String> configParams = new HashMap<>();
        if (pInitParams != null) {
            for (int i = 0; i < pInitParams.length; i += 2) {
                configParams.put(pInitParams[i],pInitParams[i+1]);
            }
            for (Map.Entry<String,String> entry : configParams.entrySet()) {
                EasyMock.expect(config.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
            }
        }

        final Vector<String> paramNames = new Vector<>(configParams.keySet());
        EasyMock.expect(config.getInitParameterNames()).andAnswer(paramNames::elements).anyTimes();
    }

    /**
     * Prepare a servlet context Mock so that the config parameters are returned properly
     *
     * @param pContext mocked context
     * @param pContextParams context parameters to return
     */
    public static void prepareServletContextMock(ServletContext pContext, String ... pContextParams) {
        Map<String,String> configParams = new HashMap<>();
        if (pContextParams != null) {
            for (int i = 0; i < pContextParams.length; i += 2) {
                configParams.put(pContextParams[i],pContextParams[i+1]);
            }
            for (Map.Entry<String,String> entry : configParams.entrySet()) {
                EasyMock.expect(pContext.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
            }
        }
        final Vector<String> paramNames = new Vector<>(configParams.keySet());
        EasyMock.expect(pContext.getInitParameterNames()).andAnswer(paramNames::elements).anyTimes();
    }
}
