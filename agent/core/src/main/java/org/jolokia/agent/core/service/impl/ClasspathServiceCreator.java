package org.jolokia.agent.core.service.impl;

import java.util.Set;

import org.jolokia.agent.core.service.JolokiaService;
import org.jolokia.agent.core.service.JolokiaServiceCreator;
import org.jolokia.agent.core.util.LocalServiceFactory;

/**
 * A request handler factory which looks up the request handler via
 * {@link LocalServiceFactory}
 *
 * @author roland
 * @since 13.06.13
 */
public class ClasspathServiceCreator implements JolokiaServiceCreator {

    private String base;

    /**
     * Create a creator with the given base name
     *
     * @param pBase base name to use
     */
    public ClasspathServiceCreator(String pBase) {
        base = pBase;
    }

    /** {@inheritDoc} */
    public Set<JolokiaService> getServices() {
        return LocalServiceFactory.createServicesAsSet("META-INF/jolokia/" + base + "-default",
                                                       "META-INF/jolokia/" + base);
    }
}
