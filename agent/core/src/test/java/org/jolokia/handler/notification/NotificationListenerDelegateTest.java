package org.jolokia.handler.notification;

import java.util.*;

import javax.management.*;

import org.easymock.IArgumentMatcher;
import org.jolokia.backend.executor.AbstractMBeanServerExecutor;
import org.jolokia.notification.BackendCallback;
import org.jolokia.request.notification.AddCommand;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 20.03.13
 */
public class NotificationListenerDelegateTest {

    private ObjectName TEST_NAME;
    private MBeanServerConnection       connection;
    private AbstractMBeanServerExecutor executor;

    @BeforeMethod
    public void setup() throws MalformedObjectNameException {
        TEST_NAME = new ObjectName("test:type=test");
        connection = createMock(MBeanServerConnection.class);
        executor = new AbstractMBeanServerExecutor() {
            @Override
            protected Set<MBeanServerConnection> getMBeanServers() {
                return new HashSet<MBeanServerConnection>(Arrays.asList(connection));
            }
        };
    }

    private ListenerRegistration createRegistration(String name,List<String> filter,Object handback, BackendCallback callback, String ... configKeyAndValue) throws MalformedObjectNameException {
        AddCommand cmd = createMock(AddCommand.class);
        expect(cmd.getObjectName()).andStubReturn(new ObjectName(name));
        expect(cmd.getFilter()).andStubReturn(filter);
        expect(cmd.getHandback()).andStubReturn(handback);
        if (configKeyAndValue.length > 0) {
            Map config = new HashMap();
            for (int i = 0; i < configKeyAndValue.length; i +=2) {
                config.put(configKeyAndValue[i],configKeyAndValue[i+1]);
            }
            expect(cmd.getConfig()).andStubReturn(config);
        } else {
            expect(cmd.getConfig()).andStubReturn(null);
        }
        replay(cmd);
        return new ListenerRegistration(cmd,callback);
    }

    @Test
    public void testCleanup() throws Exception {
        NotificationListenerDelegate delegate = new NotificationListenerDelegate();

        expect(connection.queryNames(TEST_NAME, null)).andStubReturn(Collections.singleton(TEST_NAME));
        connection.addNotificationListener(eq(TEST_NAME), eq(delegate), eqNotificationFilter("type.jmx"), isA(ListenerRegistration.class));
        connection.removeNotificationListener(eq(TEST_NAME), eq(delegate), eqNotificationFilter("type.jmx"), isA(ListenerRegistration.class));
        replay(connection);

        String id = delegate.register();
        Object handback = new Object();
        ListenerRegistration reg = createRegistration(TEST_NAME.toString(), Arrays.asList("type.jmx"), handback,null);
        delegate.addListener(executor,id,reg);
        delegate.cleanup(executor,System.currentTimeMillis() + 10000);
        try {
            delegate.refresh(id);
            fail("Client should not be registered");
        } catch (IllegalArgumentException exp) {

        }
    }

    @Test
    public void testToJson() throws Exception {
        Object handback = new Object();
        ListenerRegistration reg = createRegistration(TEST_NAME.toString(), Arrays.asList("type.jmx"), handback, null, "eins", "zwei");
        JSONObject ret = reg.toJson();
        assertEquals(ret.get("mbean"),TEST_NAME.toString());
        assertEquals(ret.get("handback"),handback);
        assertEquals(((List) ret.get("filter")).get(0),"type.jmx");
        assertEquals(((List) ret.get("filter")).size(),1);
        Map config = (Map) ret.get("config");
        assertEquals(config.get("eins"),"zwei");
    }

    @Test
    public void testHandleNotification() throws Exception {
        NotificationListenerDelegate delegate = new NotificationListenerDelegate();

        final Object handback = new Object();
        ListenerRegistration reg = createRegistration(TEST_NAME.toString(),Arrays.asList("type.jmx"),handback,new BackendCallback() {
            public void handleNotification(Notification notification, Object pHandback) {
                assertEquals(notification.getType(),"type.jmx");
                assertEquals(notification.getSource(),NotificationListenerDelegateTest.this);
                assertEquals(notification.getSequenceNumber(),1L);
                assertEquals(pHandback,handback);
            }
        });

        Notification notif = new Notification("type.jmx",this,1L);
        delegate.handleNotification(notif,reg);
    }

    public NotificationFilter eqNotificationFilter(final String ... filters) {
        reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                NotificationFilterSupport filter = (NotificationFilterSupport) argument;
                for (String f : filters) {
                   if (!filter.getEnabledTypes().contains(f)) {
                        return false;
                    }
                }
                return filters.length == filter.getEnabledTypes().size();
            }

            public void appendTo(StringBuffer buffer) {
                buffer.append("eqNotificationFilter[");
                for (String f : filters) {
                    buffer.append(f).append(",");
                }
                buffer.setLength(buffer.length() - 1);
                buffer.append("]");
            }
        });
        return null;
    }
}
