package org.jolokia.backend.dispatcher;

import java.io.IOException;
import java.util.List;

import javax.management.JMException;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.RequestType;
import org.jolokia.util.TestJolokiaContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.09.13
 */
public class RequestDispatcherImplTest {

    private JmxRequest request;

    @BeforeMethod
    public void setUp() throws Exception {
        request = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory").pathParts("used").build();

    }

    @Test
    public void requestDispatcherNoHandlingHandler() throws JMException, IOException, NotChangedException {
        TestRequestHandler testHandler = new TestRequestHandler(false);
        JolokiaContext context = new TestJolokiaContext.Builder()
                .services(RequestHandler.class,testHandler)
                .build();
        RequestDispatcherImpl requestDispatcher = new RequestDispatcherImpl(context);
        assertNull(requestDispatcher.dispatch(request));
        assertFalse(testHandler.handleRequestCalled);
    }


    @Test
    public void withoutPathParts() throws NotChangedException, IOException, JMException {
        callRequestHandler(false,null);
    }

    @Test
    public void withPathParts() throws NotChangedException, IOException, JMException {
        callRequestHandler(true,request.getPathParts());
    }

    private void callRequestHandler(boolean withPath, List<String> pathParts) throws JMException, IOException, NotChangedException {
        TestRequestHandler h1 = new TestRequestHandler(false);
        TestRequestHandler h2 = new TestRequestHandler(true,withPath);
        TestRequestHandler h3 = new TestRequestHandler(true);

        JolokiaContext context = new TestJolokiaContext.Builder()
                .services(RequestHandler.class,h1,h2,h3)
                .build();
        RequestDispatcherImpl requestDispatcher = new RequestDispatcherImpl(context);
        DispatchResult result = requestDispatcher.dispatch(request);
        assertEquals(result.getValue(),h2.returnValue);
        assertEquals(result.getPathParts(),pathParts);
        assertFalse(h1.handleRequestCalled);
        assertTrue(h2.handleRequestCalled);
        assertFalse(h3.handleRequestCalled);
    }




    private static class TestRequestHandler implements RequestHandler {

        private boolean canHandle;
        private boolean useReturnValueWithPath;

        private boolean handleRequestCalled = false;

        private Object returnValue = new Object();

        // For comparing
        private static int MAX_ID = 0;
        private int id = 0;

        private TestRequestHandler(boolean pCanHandle) {
            this(pCanHandle,false);
        }

        private TestRequestHandler(boolean pCanHandle, boolean pUseReturnValueWithPath) {
            canHandle = pCanHandle;
            useReturnValueWithPath = pUseReturnValueWithPath;
            id = MAX_ID++;
        }

        public Object handleRequest(JmxRequest pJmxReq) throws JMException, IOException, NotChangedException {
            handleRequestCalled = true;
            return returnValue;
        }

        public boolean canHandle(JmxRequest pJmxRequest) {
            return canHandle;
        }

        public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
            return useReturnValueWithPath;
        }

        public int getOrder() {
            return 0;
        }

        public Class<RequestHandler> getType() {
            return null;
        }

        public void destroy() throws Exception {
        }

        public void init(JolokiaContext pJolokiaContext) {
        }

        public int compareTo(RequestHandler o) {
            return id - ((TestRequestHandler) o).id;
        }
    }
}
