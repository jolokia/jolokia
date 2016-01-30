package org.jolokia.handler;

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

import java.util.HashMap;
import java.util.Map;

import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.RequestType;

/**
 * A request handler manager is responsible for managing so called "request handlers" which
 * are used to dispatch for all command types known to Jolokia
 *
 * @author roland
 * @since Nov 13, 2009
 */
public class RequestHandlerManager {

    // Map with all json request handlers
    private final Map<RequestType, AbstractJsonRequestHandler> requestHandlerMap = new HashMap<RequestType, AbstractJsonRequestHandler>();

    /**
     * Manager and dispatcher for incoming requests
     *
     * @param pConfig configuration from which to obtain agent meta information
     * @param pConverters string/object converters
     * @param pServerHandle server handle for obtaining MBeanServer
     * @param pRestrictor handler for access restrictions
     */
    public RequestHandlerManager(Configuration pConfig, Converters pConverters, ServerHandle pServerHandle, Restrictor pRestrictor) {
        AbstractJsonRequestHandler handlers[] = {
                new ReadHandler(pRestrictor, pConfig),
                new WriteHandler(pRestrictor, pConfig, pConverters),
                new ExecHandler(pRestrictor, pConfig, pConverters),
                new ListHandler(pRestrictor, pConfig),
                new VersionHandler(pRestrictor, pConfig, pServerHandle),
                new SearchHandler(pRestrictor, pConfig)
        };
        for (AbstractJsonRequestHandler handler : handlers) {
            requestHandlerMap.put(handler.getType(),handler);
        }
    }

    /**
     * Manager and dispatcher for incoming requests
     *
     * @param pConverters string/object converters
     * @param pServerHandle server handle for obtaining MBeanServer
     * @param pRestrictor handler for access restrictions
     */
    public RequestHandlerManager(Converters pConverters, ServerHandle pServerHandle, Restrictor pRestrictor) {
        this(null,pConverters,pServerHandle,pRestrictor);
    }

    /**
     * Get the request handler for the given type
     *
     * @param pType type of request
     * @return handler which can handle requests of the given type
     */
    public AbstractJsonRequestHandler getRequestHandler(RequestType pType) {
        AbstractJsonRequestHandler handler = requestHandlerMap.get(pType);
        if (handler == null) {
            throw new UnsupportedOperationException("Unsupported operation '" + pType + "'");
        }
        return handler;
    }

}
