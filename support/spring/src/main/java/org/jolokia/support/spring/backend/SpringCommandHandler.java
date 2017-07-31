package org.jolokia.support.spring.backend;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.springframework.context.ApplicationContext;

/**
 * Base class for Jolokia commands accessing the spring container
 *
 * @author roland
 * @since 02.12.13
 */
public abstract class SpringCommandHandler<T extends JolokiaRequest> {

    // Spring application context
    private ApplicationContext applicationContext;

    // The jolokia context used
    private JolokiaContext context;

    protected SpringCommandHandler(ApplicationContext pAppContext, JolokiaContext pContext, RequestType pType) {
        this.context = pContext;
        this.type = pType;
        this.applicationContext = pAppContext;
    }

    // Request type of this command
    private RequestType type;

    public RequestType getType() {
        return type;
    }

    public JolokiaContext getJolokiaContext() {
        return context;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public abstract Object handleRequest(T pJmxReq, Object pPreviousResult) throws InstanceNotFoundException, AttributeNotFoundException;
}
