package org.jolokia.osgi.servlet;

import java.util.Set;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.service.JolokiaContext;
import org.osgi.framework.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 23.09.13
 */
public class OsgiJolokiaServiceFactoryTest {

    private BundleContext ctx;
    private ServiceListener listener;
    private Filter filter;
    private OsgiJolokiaServiceFactory factory;

    @BeforeMethod
    public void setup() throws InvalidSyntaxException {
        ctx = createMock(BundleContext.class);
        factory = new OsgiJolokiaServiceFactory(ctx);

        filter = createMock(Filter.class);
        String filterExpr = "(objectClass=org.jolokia.backend.dispatcher.RequestHandler)";
        expect(ctx.createFilter(filterExpr)).andReturn(filter);

        listener = null;
        ctx.addServiceListener(getServiceListener(), eq(filterExpr));
    }

    @Test
    public void simpleService() throws Exception {
        ServiceReference serviceRef = createServiceReference();
        JolokiaContext jolokiaContext = createMock(JolokiaContext.class);

        RequestHandler requestHandler = createRequestHandler(serviceRef);

        requestHandler.init(jolokiaContext);
        requestHandler.destroy();
        expect(ctx.ungetService(serviceRef)).andReturn(true);
        ctx.removeServiceListener(getServiceListener());

        replay(ctx, jolokiaContext, filter, serviceRef, requestHandler);

        factory.init(jolokiaContext);
        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
        assertEquals(handler.size(),1);
        assertEquals(handler.iterator().next(),requestHandler);

        factory.destroy();

        verify(ctx, filter, serviceRef, requestHandler, jolokiaContext);
    }

    @Test(expectedExceptions = ServiceException.class, expectedExceptionsMessageRegExp = "^.*not yet initialized.*$")
    public void notInitialized() throws Exception {
        JolokiaContext jolokiaContext = createMock(JolokiaContext.class);

        ServiceReference serviceRef = createServiceReference();
        RequestHandler requestHandler = createRequestHandler(serviceRef);

        replay(ctx, serviceRef, requestHandler);
        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
    }

    @Test()
    public void exceptionOnDestroy() throws Exception {
        ServiceReference serviceRef = createServiceReference();
        JolokiaContext jolokiaContext = createMock(JolokiaContext.class);

        RequestHandler requestHandler = createRequestHandler(serviceRef);

        requestHandler.init(jolokiaContext);
        expect(ctx.ungetService(serviceRef)).andReturn(true);
        requestHandler.destroy();
        expectLastCall().andThrow(new IllegalStateException("Forced error"));
        ctx.removeServiceListener(getServiceListener());

        replay(ctx, jolokiaContext, filter, serviceRef, requestHandler);

        factory.init(jolokiaContext);
        factory.getServices(RequestHandler.class);

        try {
            factory.destroy();
            fail("Expected exception");
        } catch (ServiceException exp) {
            // Expected exception
        }

        verify(ctx, filter, serviceRef, requestHandler, jolokiaContext);
    }

    private RequestHandler createRequestHandler(ServiceReference pServiceRef) {
        RequestHandler requestHandler = createMock(RequestHandler.class);
        expect(requestHandler.compareTo(requestHandler)).andStubReturn(0);
        expect(ctx.getService(pServiceRef)).andReturn(requestHandler);
        return requestHandler;
    }

    private ServiceReference createServiceReference() throws InvalidSyntaxException {
        ServiceReference serviceRef = createMock(ServiceReference.class);
        expect(ctx.getServiceReferences(RequestHandler.class.getName(),null)).andReturn(new ServiceReference[] {
                serviceRef
        });
        return serviceRef;
    }


    @Test
    public void noService() throws InvalidSyntaxException {
        expect(ctx.getServiceReferences(RequestHandler.class.getName(),null)).andReturn(new ServiceReference[0]);
        ctx.removeServiceListener(getServiceListener());
        replay(ctx, filter);

        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
        assertEquals(handler.size(),0);
        factory.destroy();

        verify(ctx, filter);
    }

    private ServiceListener getServiceListener() {
        EasyMock.reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                if (listener != null) {
                    // Second call compares
                    return listener.equals(argument);
                } else {
                    // First call remembers
                    listener = (ServiceListener) argument;
                    return true;
                }
            }

            public void appendTo(StringBuffer buffer) {
            }
        });
        return null;
    }
}
