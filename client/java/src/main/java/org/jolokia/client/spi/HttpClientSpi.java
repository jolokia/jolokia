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
package org.jolokia.client.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jolokia.client.JolokiaQueryParameter;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.HttpMethod;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.json.JSONStructure;

/**
 * <p>Implementation-agnostic HTTP client SPI. The SPI implementation is provided by selected Jolokia library and defaults
 * to an implementation based on JDK HTTP Client.</p>
 *
 * @param <T>
 */
public interface HttpClientSpi<T> extends Closeable {

    /**
     * Send a {@link JolokiaRequest} using desired {@link HttpMethod} and query parameters. Optionally
     * pass {@link JolokiaTargetConfig proxy configuration} if the target Jolokia Agent is running in Proxy mode.
     *
     * @param request
     * @param method
     * @param parameters
     * @param targetConfig
     * @return
     * @param <REQ>
     * @param <RES>
     * @throws IOException
     */
    <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(REQ request, HttpMethod method, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig targetConfig)
            throws IOException, J4pException;

    /**
     * Send multiple {@link JolokiaRequest requests} in a single HTTP request (a <em>bulk request</em>).
     * Optionally pass {@link JolokiaTargetConfig proxy configuration} if the target Jolokia Agent is running in Proxy mode.
     * With bulk requests we can only use {@link HttpMethod#POST} method.
     *
     * @param requests
     * @param parameters
     * @param targetConfig
     * @return
     * @param <REQ>
     * @param <RES>
     * @throws IOException
     */
    <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(List<REQ> requests, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig targetConfig)
            throws IOException, J4pException;

    /**
     * Retrieve underlying, implementation-specific HTTP Client if it matches the passed type.
     *
     * @param clientClass
     * @return
     */
    T getClient(Class<T> clientClass);

}
