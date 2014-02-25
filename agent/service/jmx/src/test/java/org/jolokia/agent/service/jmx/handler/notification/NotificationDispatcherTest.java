package org.jolokia.agent.service.jmx.handler.notification;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.agent.core.service.ServerHandle;
import org.jolokia.agent.core.config.ConfigKey;
import org.jolokia.agent.core.request.notification.*;
import org.jolokia.agent.core.service.AbstractJolokiaService;
import org.jolokia.agent.core.service.notification.*;
import org.jolokia.agent.core.util.TestJolokiaContext;
import org.jolokia.agent.core.util.jmx.MBeanServerExecutor;
import org.jolokia.agent.core.util.jmx.SingleMBeanServerExecutor;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 20.03.13
 */
public class NotificationDispatcherTest {

    private static ObjectName TEST_NAME;

    static {
        try {
            TEST_NAME = new ObjectName("test:type=test");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException();
        }
    }

    private NotificationDispatcher dispatcher;
    private MBeanServerConnection connection;
    private MBeanServerExecutor executor;

    private TestJolokiaContext ctx;
    private NotificationBackend pullBackend;

    @BeforeMethod
    public void setup() {
        ServerHandle serverHandle = ServerHandle.NULL_SERVER_HANDLE;
        pullBackend = new TestNotificationBackend();
        ctx = new TestJolokiaContext.Builder()
                .serverHandle(serverHandle)
                .config(ConfigKey.AGENT_ID,"test")
                .services(NotificationBackend.class,pullBackend)
                .build();
        dispatcher = new NotificationDispatcher(ctx);
        connection = createMock(MBeanServerConnection.class);
        executor = new SingleMBeanServerExecutor(connection);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // Unregister all MBeans
        ctx.destroy();
    }

    @Test
    public void testRegister() throws Exception {
        String ret = registerClient();
        assertNotNull(ret);
    }

    private String registerClient() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, MBeanException, IOException, ReflectionException {
        RegisterCommand cmd = createCommand(RegisterCommand.class);
        JSONObject config = dispatch(cmd);
        return (String) config.get("id");
    }

    @Test
    public void testUnregisterAndPing() throws Exception {
        String id = registerClient();

        UnregisterCommand uregCmd = createCommand(UnregisterCommand.class, "client", id);
        PingCommand pingCmd = createCommand(PingCommand.class, "client", id);
        dispatch(pingCmd);
        dispatch(uregCmd);
        try {
            dispatch(pingCmd);
            fail("Client with id " + id + " should be unregistered");
        } catch (IllegalArgumentException exp) {

        }
    }

    @Test
    public void testUnregisterWithListeners() throws Exception {
        String id = registerClient();
        setupConnectionForAdd();
        AddCommand addCmd = createCommand(AddCommand.class,"client",id,"mbean",TEST_NAME.toString(),"mode","test");
        String handle = dispatch(addCmd);
        UnregisterCommand uregCmd = createCommand(UnregisterCommand.class,"client",id);
        dispatch(uregCmd);
    }

    @Test
    public void testAddAndRemove() throws Exception {

        String id = registerClient();
        setupConnectionForAdd();
        AddCommand addCmd = createCommand(AddCommand.class,"client",id,"mbean",TEST_NAME.toString(),"mode","test");
        String handle = dispatch(addCmd);
        ListCommand listCmd = createCommand(ListCommand.class,"client",id);
        JSONObject list = dispatch(listCmd);
        assertEquals(list.size(),1);
        assertEquals( ((JSONObject) list.get("1")).get("mbean"),TEST_NAME.toString());
        assertNotNull(handle);
        RemoveCommand removeCommand = createCommand(RemoveCommand.class,"client",id,"handle",handle);
        dispatch(removeCommand);
    }

    private void setupConnectionForAdd() throws IOException, InstanceNotFoundException, NoSuchFieldException, IllegalAccessException, ListenerNotFoundException {
        expect(connection.queryNames(TEST_NAME, null)).andStubReturn(Collections.singleton(TEST_NAME));
        connection.addNotificationListener(EasyMock.eq(TEST_NAME), EasyMock.eq(getNotificationListener()), (NotificationFilter) EasyMock.isNull(), EasyMock.isA(ListenerRegistration.class));
        connection.removeNotificationListener(EasyMock.eq(TEST_NAME), EasyMock.eq(getNotificationListener()), (NotificationFilter) EasyMock.isNull(), EasyMock.isA(ListenerRegistration.class));
        replay(connection);
    }

    @Test
    public void testList() throws Exception {
        String id = registerClient();
        ListCommand cmd = createCommand(ListCommand.class,"client",id);
        JSONObject list = dispatch(cmd);
        assertEquals(list.size(),0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*bla.*")
    public void testUnknownBackend() throws Exception {
        String id = registerClient();
        AddCommand cmd = createCommand(AddCommand.class,"client",id,"mode","bla","mbean",TEST_NAME.toString());
        dispatch(cmd);
    }

    private NotificationListener getNotificationListener() throws NoSuchFieldException, IllegalAccessException {
        return (NotificationListener) getField(dispatcher,"listenerDelegate");
    }

    private <T> T getField(Object pObject, String pField) throws NoSuchFieldException, IllegalAccessException {
        Field field = pObject.getClass().getDeclaredField(pField);
        field.setAccessible(true);
        return (T) field.get(pObject);
    }

    private <T> T dispatch(NotificationCommand cmd) throws MBeanException, IOException, ReflectionException {
        return (T) dispatcher.dispatch(executor,cmd);
    }
    private <T extends NotificationCommand> T createCommand(Class<T> pClass, Object ... keyValues) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<T> constructor;
        if (keyValues.length == 0) {
            constructor = pClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } else {
            constructor = pClass.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            Map args = new HashMap();
            for (int i = 0; i < keyValues.length; i+=2) {
                args.put(keyValues[i],keyValues[i+1]);
            }
            return constructor.newInstance(args);
        }
    }

    private class TestNotificationBackend extends AbstractJolokiaService<NotificationBackend> implements NotificationBackend {

        protected TestNotificationBackend() {
            super(NotificationBackend.class, 0);
        }

        public String getNotifType() {
            return "test";
        }

        public BackendCallback subscribe(NotificationSubscription pSubscription) {
            return null;
        }

        public void unsubscribe(String pClientId, String pHandle) {

        }

        public void unregister(String pClientId) {

        }

        public Map<String, ?> getConfig() {
            return new HashMap<String, Object>();
        }
    }
}
