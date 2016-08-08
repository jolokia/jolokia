package org.jolokia.server.core.service.request;

import org.jolokia.server.core.request.JolokiaObjectNameRequest;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.AbstractJolokiaService;

/**
 * Base class for request handlers which provides some utilities methods like deciding on
 * a request based on a configured provider.
 *
 * @author roland
 * @since 21.11.13
 */
public abstract class AbstractRequestHandler extends AbstractJolokiaService<RequestHandler>
        implements RequestHandler {

    // Provider of this request handler
    private String provider;

    /** {@inheritDoc} */
    protected AbstractRequestHandler(String pProvider,int pOrderId) {
        super(RequestHandler.class, pOrderId);
        this.provider = pProvider;
    }

    /** {@inheritDoc} */
    // Returns if type matches
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return !pJolokiaRequest.isExclusive() || checkProvider(pJolokiaRequest);
    }

    /**
     * Check whether the given request match the provider for which this handler is responsible
     *
     * @param pRequest request to check
     * @return true if this handler can handle this.
     */
    protected boolean checkProvider(JolokiaRequest pRequest) {
        return pRequest instanceof JolokiaObjectNameRequest &&
               provider.equals(((JolokiaObjectNameRequest) pRequest).getProvider());
    }

    /** {@inheritDoc} */
    public String getProvider() {
        return provider;
    }

    /**
     * Default implementation doesn't return any extra information, but <code>null</code>.
     *
     * @return extra runtime information to add for a version request
     */
    public Object getRuntimeInfo() {
        return null;
    }

}
