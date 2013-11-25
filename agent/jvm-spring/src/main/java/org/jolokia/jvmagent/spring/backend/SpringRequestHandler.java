package org.jolokia.jvmagent.spring.backend;

import java.io.IOException;

import javax.management.JMException;

import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JolokiaRequest;
import org.jolokia.service.AbstractJolokiaService;

/**
 * A request handler which expose the Spring application context over the
 * the Jolokia protocol
 *
 * @author roland
 * @since 22.10.13
 */
public class SpringRequestHandler extends AbstractJolokiaService<RequestHandler>
        implements RequestHandler {

    /**
     * Construction of a spring request handler
     *
     * @param pOrderId order id. A user of JolokiaService <em>must ensure</em> that the given
     *                 order id is unique for the given type. It used for ordering the services but is also
     *                 used as an id when storing it in  a set.
     */
    protected SpringRequestHandler(int pOrderId) {
        super(RequestHandler.class, pOrderId);
    }

    /** {@inheritDoc} */
    public Object handleRequest(JolokiaRequest pJmxReq) throws JMException, IOException, NotChangedException {
        return null;
    }

    /** {@inheritDoc} */
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        // return checkRealm("spring",pJolokiaRequest);
        return false;
    }

    /** {@inheritDoc} */
    public boolean useReturnValueWithPath(JolokiaRequest pJolokiaRequest) {
        return false;
    }
}
