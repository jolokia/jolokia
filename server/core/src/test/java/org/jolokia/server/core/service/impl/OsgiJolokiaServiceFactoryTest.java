package org.jolokia.server.core.service.impl;

import java.util.Set;

import org.easymock.EasyMock;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.request.RequestHandler;
import org.osgi.framework.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.09.13
 */
public class OsgiJolokiaServiceFactoryTest {

    private BundleContext bundleContext;
    private OsgiJolokiaServiceFactory factory;

    @BeforeMethod
    public void setup() throws InvalidSyntaxException {
        bundleContext = createMock(BundleContext.class);
        factory = new OsgiJolokiaServiceFactory(bundleContext);

        addServiceLookup(RequestHandler.class);
        addServiceLookup(JolokiaService.class);
    }

    private void addServiceLookup(Class<?> pServiceClass) throws InvalidSyntaxException {
        expect(bundleContext.createFilter("(objectClass=" + pServiceClass.getName()+ ")"))
                .andStubReturn(createMock(Filter.class));
        bundleContext.addServiceListener(EasyMock.anyObject(), eq("(objectClass=" + pServiceClass.getName() + ")"));
        expectLastCall().asStub();
        expect(bundleContext.getServiceReferences(pServiceClass.getName(), null)).andStubReturn(null);
        bundleContext.removeServiceListener(EasyMock.anyObject());
        expectLastCall().asStub();
    }

    @Test
    public void simpleService() throws Exception {
        @SuppressWarnings("unchecked")
        ServiceReference<RequestHandler> serviceRef = (ServiceReference<RequestHandler>) createServiceReference();
        JolokiaContext jolokiaContext = createMock(JolokiaContext.class);

        RequestHandler requestHandler = createRequestHandler(serviceRef);

        requestHandler.init(jolokiaContext);
        requestHandler.destroy();
        expect(bundleContext.ungetService(serviceRef)).andReturn(true);

        replay(bundleContext, jolokiaContext, serviceRef, requestHandler);

        factory.init(jolokiaContext);
        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
        assertEquals(handler.size(),1);
        assertEquals(handler.iterator().next(),requestHandler);

        factory.destroy();

        verify(bundleContext, serviceRef, requestHandler, jolokiaContext);
    }

    @Test(expectedExceptions = ServiceException.class, expectedExceptionsMessageRegExp = "^.*not yet initialized.*$")
    public void notInitialized() throws Exception {
        JolokiaContext jolokiaContext = createMock(JolokiaContext.class);

        @SuppressWarnings("unchecked")
        ServiceReference<RequestHandler> serviceRef = (ServiceReference<RequestHandler>) createServiceReference();
        RequestHandler requestHandler = createRequestHandler(serviceRef);

        replay(bundleContext, serviceRef, requestHandler);
        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
    }

    @Test()
    public void exceptionOnDestroy() throws Exception {
        @SuppressWarnings("unchecked")
        ServiceReference<RequestHandler> serviceRef = (ServiceReference<RequestHandler>) createServiceReference();
        JolokiaContext jolokiaContext = createMock(JolokiaContext.class);

        RequestHandler requestHandler = createRequestHandler(serviceRef);

        requestHandler.init(jolokiaContext);
        expect(bundleContext.ungetService(serviceRef)).andReturn(true);
        requestHandler.destroy();
        expectLastCall().andThrow(new IllegalStateException("Forced error"));

        replay(bundleContext, jolokiaContext,  serviceRef, requestHandler);

        factory.init(jolokiaContext);
        factory.getServices(RequestHandler.class);

        try {
            factory.destroy();
            fail("Expected exception");
        } catch (ServiceException exp) {
            // Expected exception
        }

        verify(bundleContext, serviceRef, requestHandler, jolokiaContext);
    }

    @SuppressWarnings("EqualsWithItself")
    private RequestHandler createRequestHandler(ServiceReference<RequestHandler> pServiceRef) {
        RequestHandler requestHandler = createMock(RequestHandler.class);
        expect(requestHandler.compareTo(requestHandler)).andStubReturn(0);
        expect(bundleContext.getService(pServiceRef)).andReturn(requestHandler);
        return requestHandler;
    }

    private ServiceReference<?> createServiceReference() throws InvalidSyntaxException {
        ServiceReference<?> serviceRef = createMock(ServiceReference.class);
        expect(bundleContext.getServiceReferences(RequestHandler.class.getName(),null)).andReturn(new ServiceReference[]{
                serviceRef
        });
        return serviceRef;
    }


    @Test
    public void noService() throws InvalidSyntaxException {
        expect(bundleContext.getServiceReferences(RequestHandler.class.getName(),null)).andReturn(new ServiceReference[0]);
        replay(bundleContext);

        Set<RequestHandler> handler = factory.getServices(RequestHandler.class);
        assertEquals(handler.size(),0);
        factory.destroy();

        verify(bundleContext);
    }
}
