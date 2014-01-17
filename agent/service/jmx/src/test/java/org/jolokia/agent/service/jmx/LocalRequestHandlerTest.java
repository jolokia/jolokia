package org.jolokia.agent.service.jmx;

import java.io.IOException;
import java.lang.reflect.Field;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.backend.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.agent.service.jmx.handler.CommandHandler;
import org.jolokia.agent.service.jmx.handler.CommandHandlerManager;
import org.jolokia.jmx.MBeanServerExecutor;
import org.jolokia.request.JolokiaRequest;
import org.jolokia.request.JolokiaRequestBuilder;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.RequestType;
import org.jolokia.util.TestJolokiaContext;
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

    private CommandHandler injectCommandHandler(LocalRequestHandler pRequestHandler) throws JMException, NoSuchFieldException, IllegalAccessException {
        commandHandler = createMock(CommandHandler.class);
        CommandHandlerManager commandHandlerManager = createMock(CommandHandlerManager.class);
        expect(commandHandlerManager.getCommandHandler((RequestType) anyObject())).andStubReturn(commandHandler);
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


    @Test
    public void dispatchRequest() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        Object result = new Object();

        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(false);
        expect(commandHandler.handleRequest(EasyMock.<MBeanServerConnection>anyObject(), eq(request))).andReturn(result);
        replay(commandHandler);
        assertEquals(requestHandler.handleRequest(request,null),result);
    }


    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void dispatchRequestInstanceNotFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        dispatchWithException(new InstanceNotFoundException());
    }


    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void dispatchRequestAttributeNotFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        dispatchWithException(new AttributeNotFoundException());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void dispatchRequestIOException() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        dispatchWithException(new IOException());
    }

    private void dispatchWithException(Exception e) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(false);
        expect(commandHandler.handleRequest(EasyMock.<MBeanServerConnection>anyObject(), eq(request))).andThrow(e).anyTimes();
        replay(commandHandler);
        requestHandler.handleRequest(request,null);
    }

    @Test
    public void dispatchAtOnce() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        Object result = new Object();

        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(true);
        expect(commandHandler.handleRequest(isA(MBeanServerExecutor.class), eq(request), isNull())).andReturn(result);
        replay(commandHandler);
        assertEquals(requestHandler.handleRequest(request,null),result);
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*Internal.*")
    public void dispatchAtWithException() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        expect(commandHandler.handleAllServersAtOnce(request)).andReturn(true);
        expect(commandHandler.handleRequest(isA(MBeanServerExecutor.class), eq(request), isNull())).andThrow(new IOException());
        replay(commandHandler);
        requestHandler.handleRequest(request,null);
    }

}
