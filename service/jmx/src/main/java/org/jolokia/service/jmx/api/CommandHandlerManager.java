package org.jolokia.service.jmx.api;

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

import java.util.*;

import javax.management.JMException;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.LocalServiceFactory;
import org.jolokia.server.core.util.RequestType;

/**
 * A request handler manager is responsible for managing so called "request handlers" which
 * are used to dispatch for all command types known to the Jolokia protocol. Its main purpose
 * is to keep a mapping between request types and the handler which can deal with that
 * request type.
 *
 * @author roland
 * @since Nov 13, 2009
 */
public class CommandHandlerManager {

    // Map with all json request handlers
    private final Map<RequestType, CommandHandler<?>> requestHandlerMap = new HashMap<>();

    /**
     * Manager and dispatcher for incoming requests
     *
     * @param pCtx jolokia context
     */
    public CommandHandlerManager(JolokiaContext pCtx) {
        this(pCtx,null);
    }


    /**
     * Constructor, which creates the manager. This object can be used as a singleton
     * since it doesnt keep a reference to a request being processed
     *
     * @param pCtx jolokia context for retrieving various services
     * @param pProvider provider to use for returned names. Certain handlers need this information for returning meta
     *               data with the proper provider prefixed.
     */
    public CommandHandlerManager(JolokiaContext pCtx, String pProvider) {
        List<CommandHandler<?>> handlers =
                LocalServiceFactory.createServices (this.getClass().getClassLoader(),"META-INF/jolokia/command-handlers");
        for (CommandHandler<?> handler : handlers) {
            handler.init(pCtx,pProvider);
            requestHandlerMap.put(handler.getType(),handler);
        }
    }

    /**
     * Get the request handler for the given type
     *
     * @param pType type of request
     * @return handler which can handle requests of the given type
     */
    public <R extends JolokiaRequest> CommandHandler<R> getCommandHandler(RequestType pType) {
        CommandHandler<?> handler = requestHandlerMap.get(pType);
        if (handler == null) {
            throw new UnsupportedOperationException("Unsupported operation '" + pType + "'");
        }
        //noinspection unchecked
        return (CommandHandler<R>) handler;
    }

    /**
     * Lifecycle method called when agent goes down
     */
    public void destroy() throws JMException {
        for (CommandHandler<?> handler : requestHandlerMap.values()) {
            handler.destroy();
        }
    }
}
