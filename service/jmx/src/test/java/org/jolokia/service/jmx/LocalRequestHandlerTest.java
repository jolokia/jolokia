package org.jolokia.service.jmx;

import java.io.IOException;
import java.lang.reflect.Field;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.api.CommandHandler;
import org.jolokia.service.jmx.api.CommandHandlerManager;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 12.06.13
 */
public class LocalRequestHandlerTest {

    private JolokiaRequest request;
    private LocalRequestHandler requestHandler;
    @SuppressWarnings("rawtypes")
    private CommandHandler commandHandler;
    @BeforeMethod
    public void setup() throws JMException, NoSuchFieldException, IllegalAccessException {
        TestDetector.reset();
        JolokiaContext ctx = new TestJolokiaContext.Builder().config(ConfigKey.MBEAN_QUALIFIER,"qualifier=test").build();
        requestHandler = new LocalRequestHandler(10);
        requestHandler.init(ctx);
        commandHandler = injectCommandHandler(requestHandler);
        request = new JolokiaRequestBuilder(RequestType.READ,"java.lang:type=Memory").attribute("HeapMemoryUsage").build();
    }

    private CommandHandler<?> injectCommandHandler(LocalRequestHandler pRequestHandler) throws JMException, NoSuchFieldException, IllegalAccessException {
        commandHandler = createMock(CommandHandler.class);
        CommandHandlerManager commandHandlerManager = createMock(CommandHandlerManager.class);
        //noinspection unchecked
        expect(commandHandlerManager.getCommandHandler(anyObject())).andStubReturn(commandHandler);
        commandHandlerManager.destroy();
        expectLastCall().asStub();
        replay(commandHandlerManager);

        Field field = LocalRequestHandler.class.getDeclaredField("commandHandlerManager");
        field.setAccessible(true);
        field.set(pRequestHandler,commandHandlerManager);
        return commandHandler;
    }

    @AfterMethod
    public void tearDown() throws JMException {
        if (requestHandler != null) {
            requestHandler.destroy();
        }
    }


    @SuppressWarnings("unchecked")
    @Test
    public void dispatchRequest() throws InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException, EmptyResponseException {
        Object result = new Object();

        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(false);
        expect(commandHandler.handleSingleServerRequest(EasyMock.anyObject(), eq(request))).andReturn(result);
        replay(commandHandler);
        assertEquals(requestHandler.handleRequest(request,null),result);
    }


    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void dispatchRequestInstanceNotFound() throws InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException, EmptyResponseException {
        dispatchWithException(new InstanceNotFoundException());
    }


    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void dispatchRequestAttributeNotFound() throws InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException, EmptyResponseException {
        dispatchWithException(new AttributeNotFoundException());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void dispatchRequestIOException() throws InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException, EmptyResponseException {
        dispatchWithException(new IOException());
    }

    @SuppressWarnings("unchecked")
    private void dispatchWithException(Exception e) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException, EmptyResponseException {
        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(false);
        expect(commandHandler.handleSingleServerRequest(EasyMock.anyObject(), eq(request))).andThrow(e).anyTimes();
        replay(commandHandler);
        requestHandler.handleRequest(request,null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void dispatchAtOnce() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException, EmptyResponseException {
        Object result = new Object();

        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(true);
        expect(commandHandler.handleAllServerRequest(isA(MBeanServerAccess.class), eq(request), isNull())).andReturn(result);
        replay(commandHandler);
        assertEquals(requestHandler.handleRequest(request,null),result);
    }

    @SuppressWarnings("unchecked")
    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*Internal.*")
    public void dispatchAtWithException() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException, EmptyResponseException {
        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(true);
        expect(commandHandler.handleAllServerRequest(isA(MBeanServerAccess.class), eq(request), isNull())).andThrow(new IOException());
        replay(commandHandler);
        requestHandler.handleRequest(request,null);
    }

}
