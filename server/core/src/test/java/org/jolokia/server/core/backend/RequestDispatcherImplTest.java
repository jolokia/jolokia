package org.jolokia.server.core.backend;

import java.io.IOException;
import java.util.List;

import javax.management.JMException;

import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.09.13
 */
public class RequestDispatcherImplTest {

    private JolokiaRequest request;

    @BeforeMethod
    public void setUp() throws Exception {
        request = new JolokiaRequestBuilder(RequestType.READ,"java.lang:type=Memory").pathParts("used").build();

    }

    @Test
    public void requestDispatcherNoHandlingHandler() throws JMException, IOException, NotChangedException, EmptyResponseException {

        TestRequestHandler testHandler = new TestRequestHandler(false);
        try {
            JolokiaContext context = new TestJolokiaContext.Builder()
                    .services(RequestHandler.class,testHandler)
                    .build();
            RequestDispatcherImpl requestDispatcher = new RequestDispatcherImpl(context);
            requestDispatcher.dispatch(request);
            fail("Exception should be thrown");
        } catch (IllegalStateException exp) {
            assertFalse(testHandler.handleRequestCalled);
        }
    }


    @Test
    public void withPathParts() throws NotChangedException, IOException, JMException, EmptyResponseException {
        callRequestHandler(request.getPathParts());
    }

    private void callRequestHandler(List<String> pathParts) throws JMException, IOException, NotChangedException, EmptyResponseException {
        TestRequestHandler h1 = new TestRequestHandler(false);
        TestRequestHandler h2 = new TestRequestHandler(true);
        TestRequestHandler h3 = new TestRequestHandler(true);

        JolokiaContext context = new TestJolokiaContext.Builder()
                .services(RequestHandler.class,h1,h2,h3)
                .build();
        RequestDispatcherImpl requestDispatcher = new RequestDispatcherImpl(context);
        Object result = requestDispatcher.dispatch(request);
        assertEquals(result,h2.returnValue);
        assertEquals(request.getPathParts(),pathParts);
        assertFalse(h1.handleRequestCalled);
        assertTrue(h2.handleRequestCalled);
        assertFalse(h3.handleRequestCalled);
    }




    private static class TestRequestHandler extends AbstractRequestHandler {

        private final boolean canHandle;

        private boolean handleRequestCalled = false;

        private final Object returnValue = new Object();

        // For comparing
        private static int MAX_ID = 0;
        private int id = 0;

        private TestRequestHandler(boolean pCanHandle) {
            super("test",0);
            canHandle = pCanHandle;
            id = MAX_ID++;
        }

        public Object handleRequest(JolokiaRequest pJmxReq, Object pPrevious) {
            handleRequestCalled = true;
            return returnValue;
        }

        public boolean canHandle(JolokiaRequest pJolokiaRequest) {
            return canHandle;
        }

        public Class<RequestHandler> getType() {
            return null;
        }

        public void destroy() {
        }

        public void init(JolokiaContext pJolokiaContext) {
        }

        public int compareTo(RequestHandler pOtherService) {
            return id - ((TestRequestHandler) pOtherService).id;
        }
    }
}
