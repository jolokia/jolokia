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
import java.util.Set;

import javax.management.JMException;
import javax.management.JMRuntimeException;

import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.RequestHandler;

/**
 * Manager object responsible for finding a {@link RequestHandler} and
 * dispatching the request to be processed by the handler. The JSON (de)serialization is not a concern here and
 * is a responsibility of the caller. So this class is a gateway into the core part of Jolokia.
 *
 * @author roland
 * @since 11.06.13
 */
class RequestDispatcherImpl implements RequestDispatcher {

    // Service manager for looking up services
    private final JolokiaContext jolokiaContext;

    /**
     * Create a dispatcher which is used to select the {@link RequestHandler} for processing the request
     *
     * @param pPJolokiaContext service manager for looking up all services
     */
    RequestDispatcherImpl(JolokiaContext pPJolokiaContext) {
        jolokiaContext = pPJolokiaContext;
    }

    @Override
    public Object dispatch(JolokiaRequest pJolokiaRequest)
            throws IOException, JMException, JMRuntimeException, NotChangedException, BadRequestException, EmptyResponseException {

        // Request handlers are looked up each time to cope with the dynamics e.g. in OSGi envs.
        boolean found = false;
        Object result = null;

        Set<RequestHandler> handlers = jolokiaContext.getServices(RequestHandler.class);
        for (RequestHandler requestHandler : handlers) {
            if (requestHandler.canHandle(pJolokiaRequest)) {
                if (pJolokiaRequest.isExclusive()) {
                    // call this request handler and return the value - no more request handlers
                    // will be processed
                    return requestHandler.handleRequest(pJolokiaRequest, null);
                } else {
                    // non-exclusive requests are handled by passing previous result
                    // (from previous RequestHandler)
                    result = requestHandler.handleRequest(pJolokiaRequest, result);
                }
                found = true;
            }
        }

        if (!found) {
            throw new IllegalStateException("Internal error: No request handler found for handling " + pJolokiaRequest);
        }

        return result;
    }

}
