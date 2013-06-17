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

    private String base;

    /**
     * Create a creator with the given base name
     *
     * @param pBase base name to use
     */
    public ClasspathRequestHandlerCreator(String pBase) {
        base = pBase;
    }

    /** {@inheritDoc} */
    public Set<RequestHandler> getServices() {
        return LocalServiceFactory.createServicesAsSet("META-INF/" + base + "-default",
                                                       "META-INF/" + base);
    }
}
