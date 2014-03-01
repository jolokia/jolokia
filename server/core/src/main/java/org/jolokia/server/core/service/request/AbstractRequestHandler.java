package org.jolokia.server.core.service.request;

import org.jolokia.server.core.request.JolokiaObjectNameRequest;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.AbstractJolokiaService;

/**
 * Base class for request handlers which provides some utilities methods like deciding on
 * a request based on a configured realm.
 *
 * @author roland
 * @since 21.11.13
 */
public abstract class AbstractRequestHandler extends AbstractJolokiaService<RequestHandler>
        implements RequestHandler {

    // Realm of this request handler
    private String realm;

    /** {@inheritDoc} */
    protected AbstractRequestHandler(String pRealm,int pOrderId) {
        super(RequestHandler.class, pOrderId);
        this.realm = pRealm;
    }

    /** {@inheritDoc} */
    // Returns if type matches
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return !pJolokiaRequest.isExclusive() || checkRealm(pJolokiaRequest);
    }

    /**
     * Check whether the given request match the realm for which this handler is responsible
     *
     * @param pRequest request to check
     * @return true if this handler can handle this.
     */
    protected boolean checkRealm(JolokiaRequest pRequest) {
        return pRequest instanceof JolokiaObjectNameRequest &&
               realm.equals(((JolokiaObjectNameRequest) pRequest).getRealm());
    }

    /** {@inheritDoc} */
    public String getRealm() {
        return realm;
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
