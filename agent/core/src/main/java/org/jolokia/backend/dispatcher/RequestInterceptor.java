package org.jolokia.backend.dispatcher;

import org.jolokia.request.JolokiaRequest;
import org.jolokia.service.JolokiaService;
import org.json.simple.JSONObject;

/**
 * Interface describing an interceptor wrapping around a request processing.
 * As input it gets the original {@link JolokiaRequest} and the original {@link JSONObject} response
 * sent back to the client. The interceptor might add to the return value any extra
 * information to be sent to the client.
 *
 * @author roland
 * @since 09.09.13
 */
public interface RequestInterceptor extends JolokiaService<RequestInterceptor> {

    /**
     * Log the call to the given request. For bulk requests, this method
     * is called multiple times, once for each included request.
     *
     * @param pRequest request received
     * @param pRetValue the value to be returned.
     */
    void intercept(JolokiaRequest pRequest, JSONObject pRetValue);
}
