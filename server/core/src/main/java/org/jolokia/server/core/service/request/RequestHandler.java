/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.service.request;

import java.io.IOException;

import javax.management.JMException;
import javax.management.JMRuntimeException;

import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaService;

/**
 * Interface for handling certain {@link JolokiaRequest Jolokia requests}
 *
 * @author roland
 * @since Nov 11, 2009
 */
public interface RequestHandler extends JolokiaService<RequestHandler> {

    /**
     * First, check whether this {@link RequestHandler} actually handles given {@link JolokiaRequest}. There
     * may be handlers for certain types of requests or requests with some specific parameters.
     *
     * @param pJolokiaRequest request to check
     * @return true if this {@link RequestHandler} can handle the request
     */
    boolean canHandle(JolokiaRequest pJolokiaRequest);

    /**
     * <p>Handle a {@link JolokiaRequest} using specific backend (local/remote JMX, Spring application context, ...)
     * and return the result.</p>
     *
     * <p>Request can be divided can be in these categories:<ul>
     *     <li>Meta-requests - asking for configuration, version, ...</li>
     *     <li>{@link JolokiaRequest#isExclusive() Exclusive requests} which are handled by first {@link RequestHandler}
     *     and returned without checking other handlers</li>
     *     <li>Non-exclusive requests which are handled by all
     *     {@link RequestHandler#canHandle(JolokiaRequest) handlers that support the request} and a result from previous
     *     handler is passed as a 2nd argument to the next handler (so a cumulative response can be built.</li>
     * </ul></p>
     *
     * @param pJmxReq the {@link JolokiaRequest} to handle
     * @param pPreviousResult a result object from a previous {@link #handleRequest(R, Object)} call when
     *                {@link JolokiaRequest#isExclusive()} is {@code false}. This argument can be {@code null}.
     * @return result object
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection} for some commands (which may be remote)
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws NotChangedException when (according to request parameters) user wants HTTP 304 if nothing has changed
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    <R extends JolokiaRequest> Object handleRequest(R pJmxReq, Object pPreviousResult)
            throws IOException, JMException, JMRuntimeException, NotChangedException, BadRequestException, EmptyResponseException;

    /**
     * Get the <em>identifier</em> (provider) of this {@link RequestHandler}. This allows some requests to
     * target given {@link RequestHandler} by such provider ID. The most important ones are {@code jmx} for
     * <em>local</em> JMX request handler and {@code proxy} for <em>remote</em> JMX request handler.
     *
     * @return provider name for which this handler is responsible.
     */
    String getProvider();

    /**
     * Any extra runtime associated with this handler, which is used in a "version" request
     * to get information about request handlers. This means this method is not part of every request handling path.
     *
     * @return a object containing extra information and which must be serializable
     */
    Object getRuntimeInfo();

}
