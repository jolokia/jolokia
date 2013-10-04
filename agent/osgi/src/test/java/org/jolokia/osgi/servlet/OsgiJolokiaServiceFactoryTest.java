package org.jolokia.osgi.servlet;

import java.util.Set;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.backend.dispatcher.RequestHandler;
import org.osgi.framework.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;

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
    public void simpleService() throws InvalidSyntaxException {
        ServiceReference serviceRef = createMock(ServiceReference.class);
        expect(ctx.getServiceReferences(RequestHandler.class.getName(),null)).andReturn(new ServiceReference[] {
                serviceRef
        });
        RequestHandler requestHandler = createMock(RequestHandler.class);
        expect(requestHandler.compareTo(requestHandler)).andStubReturn(0);
        expect(ctx.getService(serviceRef)).andReturn(requestHandler);

        expect(ctx.ungetService(serviceRef)).andReturn(true);

        ctx.removeServiceListener(getServiceListener());
        replay(ctx, filter);

        replay(serviceRef, requestHandler);

        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
        assertEquals(handler.size(),1);
        assertEquals(handler.iterator().next(),requestHandler);

        factory.destroy();

        verify(ctx, filter, serviceRef, requestHandler);
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
