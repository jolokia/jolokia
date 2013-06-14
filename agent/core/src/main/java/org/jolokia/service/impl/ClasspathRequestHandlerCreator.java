package org.jolokia.service.impl;

import java.util.Set;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.service.JolokiaServiceCreator;

/**
 * A request handler factory which looks up the request handler via
 * {@link LocalServiceFactory}
 *
 * @author roland
 * @since 13.06.13
 */
public class ClasspathRequestHandlerCreator implements JolokiaServiceCreator<RequestHandler> {

    /** {@inheritDoc} */
    public Set<RequestHandler> getServices() {
        return LocalServiceFactory.createServicesAsSet("META-INF/request-handler-default",
                                                       "META-INF/request-handler");
    }
}
