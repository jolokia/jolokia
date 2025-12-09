package org.jolokia.server.core.service.impl;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.api.JolokiaServiceCreator;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.util.JolokiaServicesValidator;
import org.jolokia.core.util.LocalServiceFactory;

/**
 * A request handler factory which looks up the request handler via
 * {@link LocalServiceFactory}
 *
 * @author roland
 * @since 13.06.13
 */
public class ClasspathServiceCreator implements JolokiaServiceCreator {

    private final ClassLoader loader;
    private final String base;

    /**
     * Create a creator with the given base name
     *
     * @param pBase base name to use
     */
    public ClasspathServiceCreator(ClassLoader pLoader, String pBase) {
        loader = pLoader;
        base = pBase;
    }

    /** {@inheritDoc} */
    public Set<JolokiaService<?>> getServices(LogHandler logHandler) {
        List<JolokiaService<?>> services = LocalServiceFactory.createServices(loader, "META-INF/jolokia/" + base + "-default",
            "META-INF/jolokia/" + base);

        if (JolokiaServicesValidator.validateServices(services, logHandler)) {
            return new TreeSet<>(services);
        }

        return new TreeSet<>();
    }
}
