package org.jolokia.backend.dispatcher;

import java.util.*;

import org.jolokia.request.JolokiaObjectNameRequest;
import org.jolokia.request.JolokiaRequest;
import org.jolokia.service.AbstractJolokiaService;
import org.jolokia.util.RequestType;

/**
 * @author roland
 * @since 21.11.13
 */
abstract public class AbstractRequestHandler extends AbstractJolokiaService<RequestHandler>
        implements RequestHandler {

    // Realm of this request handler
    protected String realm;

    // Set of types which should be called for all realms
    private Set<RequestType> mergedTypes =
            new HashSet<RequestType>(Arrays.asList(RequestType.SEARCH,RequestType.LIST));

    /** {@inheritDoc} */
    protected AbstractRequestHandler(String pRealm,int pOrderId) {
        super(RequestHandler.class, pOrderId);
        this.realm = pRealm;
    }

    /** {@inheritDoc} */
    // Returns if type matches
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return mergedTypes.contains(pJolokiaRequest.getType()) ||
               ((pJolokiaRequest instanceof JolokiaObjectNameRequest) &&
               checkRealm((JolokiaObjectNameRequest) pJolokiaRequest));
    }

    /**
     * Check whether the given request match the realm for which this handler is responsible
     *
     * @param pRequest request to check
     * @return true if this handler can handle this.
     */
    protected boolean checkRealm(JolokiaObjectNameRequest pRequest) {
        return realm.equals(pRequest.getRealm());
    }

    /** {@inheritDoc} */
    public String getRealm() {
        return realm;
    }

    /**
     * Default implementation doesn't return any extra information, but <code>null</code>
     *
     * @return extra runtime information to add for a version request
     */
    public Object getRuntimeInfo() {
        return null;
    }

}
