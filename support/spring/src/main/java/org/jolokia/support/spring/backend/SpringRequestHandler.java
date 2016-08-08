package org.jolokia.support.spring.backend;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;

import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.springframework.context.ApplicationContext;

/**
 * A request handler which expose the Spring application context over the
 * the Jolokia protocol
 *
 * @author roland
 * @since 22.10.13
 */
public class SpringRequestHandler extends AbstractRequestHandler
        implements RequestHandler {

    public static final String PROVIDER = "spring";

    // Application context for looking up beans
    private ApplicationContext appContext;

    // Map for getting to the proper command handler
    private Map<RequestType,SpringCommandHandler> commandHandlerMap = new HashMap<RequestType, SpringCommandHandler>();

    /**
     * Construction of a spring request handler
     *
     * @param pAppContext the application context from where to fetch the spring beans
     * @param pOrder order of this service
     */
    public SpringRequestHandler(ApplicationContext pAppContext, int pOrder) {
        super(PROVIDER, pOrder);
        this.appContext = pAppContext;
    }

    /** {@inheritDoc} */
    public <R extends JolokiaRequest> Object handleRequest(R pJmxReq, Object pPreviousResult) throws JMException, IOException, NotChangedException {
        SpringCommandHandler handler = commandHandlerMap.get(pJmxReq.getType());
        if (handler == null) {
            throw new UnsupportedOperationException("No spring command handler for type " + pJmxReq.getType() + " registered");
        }
        return handler.handleRequest(pJmxReq, pPreviousResult);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        for (SpringCommandHandler handler : new SpringCommandHandler[] {
                new SpringReadHandler(appContext, pJolokiaContext),
                new SpringListHandler(appContext, pJolokiaContext)
        }) {
            commandHandlerMap.put(handler.getType(), handler);
        }
    }
}
