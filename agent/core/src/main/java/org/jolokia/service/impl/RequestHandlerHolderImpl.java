package org.jolokia.service.impl;

import java.util.Set;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.dispatcher.RequestHandlerHolder;
import org.jolokia.service.JolokiaServiceManager;

/**
 * Type proxy to the the request handlers in use. Request handler can come and
 * go.
 *
 * @author roland
 * @since 13.06.13
 */
public class RequestHandlerHolderImpl implements RequestHandlerHolder {

    private JolokiaServiceManager serviceManager;

    public RequestHandlerHolderImpl(JolokiaServiceManager pJolokiaServiceManager) {
        serviceManager = pJolokiaServiceManager;
    }

    public Set<RequestHandler> getRequestHandlers() {
        return serviceManager.getServices(RequestHandler.class);
    }
}
