package org.jolokia.server.core.service.request;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.JolokiaService;

/*
 * Copyright 2009-2013 Roland Huss
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


/**
 * Interface for dispatching a request to a certain backend.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public interface RequestHandler extends JolokiaService<RequestHandler> {
    /**
     * Dispatch a {@link JolokiaRequest} to a certain backend
     * and return the result of the JMX action. Request can be divided can be in two categories:
     * One which are dealt exclusively with a single handler within a single realm and others which response
     * is merged from the outcome from several request handlers.
     *
     * For non-exclusive requests, multiple request handlers are called in sequence,
     * where a latter request handler gets the result from a former as argument. In this
     * case the request handler must either update the give object or return a new object
     * from the same type with its own results appended.
     *
     * Each request type has a fixed type for the
     * result objects (given and to be returned):
     *
     * <dl
     *     <dt><code>list</code></dt>
     *     <dd>java.util.Map</dd>
     *
     *     <dt><code>search</code></dt>
     *     <dd>java.util.List</dd>
     * </dl>
     *
     * For exclusive requests, the given object is null
     *
     * @param pJmxReq the request to dispatch
     * @param pPreviousResult a result object from a previous {@link #handleRequest(R, Object)} call when
     *                {@link JolokiaRequest#isExclusive()} is <code>false</code>. This argument can be <code>null</code>
     * @return result object
     * @throws JMException if performing of the actions failes
     * @throws IOException if handling fails
     * @throws NotChangedException if the handled request's response hasnt changed (and the appropriate request parameter
     *         has been set).
     */
    <R extends JolokiaRequest>  Object handleRequest(R pJmxReq, Object pPreviousResult)
            throws JMException, IOException, NotChangedException;

    /**
     * Check whether current dispatcher can handle the given request
     *
     * @param pJolokiaRequest request to check
     * @return true if this dispatcher can handle the request
     */
    boolean canHandle(JolokiaRequest pJolokiaRequest);

    /**
     * Get the realm for which this handler is responsible
     *
     * @return realm name for which this handler is responsible.
     */
    String getRealm();

    /**
     * Any extra runtime associated with this handler, which is used in a "version" request
     * to get information about request handlers
     *
     * @return a object containing extra information and which must be serializable
     */
    Object getRuntimeInfo();
}
