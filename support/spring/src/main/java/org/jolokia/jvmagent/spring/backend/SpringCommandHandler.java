package org.jolokia.jvmagent.spring.backend;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;

import org.jolokia.core.request.JolokiaRequest;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.util.RequestType;
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
        context = pContext;
        type = pType;
        applicationContext = pAppContext;
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
