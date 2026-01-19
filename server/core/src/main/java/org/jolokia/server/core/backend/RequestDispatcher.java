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
package org.jolokia.server.core.backend;

import java.io.IOException;

import javax.management.JMException;
import javax.management.JMRuntimeException;

import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.request.RequestHandler;

/**
 * Manager interface for dispatching a request to one {@link RequestHandler}.
 * This is the entry point for Jolokia in order to process a request.
 *
 * @author roland
 * @since 11.06.13
 */
public interface RequestDispatcher {

    /**
     * Dispatch a request to a single {@link RequestHandler}. This results a list with zero, one or more result
     * objects. If more than one result is returned, the results must be merged.
     *
     * @param pJolokiaRequest the request to dispatch
     * @return result of the dispatch operation.
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection} for some commands (which may be remote)
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws NotChangedException when (according to request parameters) user wants HTTP 304 if nothing has changed
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    Object dispatch(JolokiaRequest pJolokiaRequest)
        throws IOException, JMException, JMRuntimeException, NotChangedException, BadRequestException, EmptyResponseException;

}
