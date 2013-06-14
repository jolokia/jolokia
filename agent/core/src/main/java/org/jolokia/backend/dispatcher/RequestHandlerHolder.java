package org.jolokia.backend.dispatcher;

import java.util.Collection;

/**
 * Interface usable for accessing the real request handlers which can
 * change dynamically
 *
 * @author roland
 * @since 13.06.13
 */
public interface RequestHandlerHolder {

    /**
     * Get all managed request handlers
     *
     * @return collection of request handlers or an empty collection.
     */
    Collection<RequestHandler> getRequestHandlers();
}
