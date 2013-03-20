package org.jolokia.handler;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;

import org.jolokia.request.JmxNotificationRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.request.notification.NotificationCommandType;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.util.RequestType;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.createMock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 20.03.13
 */
public class NotificationHandlerTest extends BaseHandlerTest {

    @Test
    public void testSimple() throws Exception {
        NotificationHandler handler = new NotificationHandler(new AllowAllRestrictor());
        assertEquals(handler.getType(), RequestType.NOTIFICATION);
        JmxNotificationRequest request = createRequest();
        // No exception for now
        handler.checkForRestriction(request);
        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        handler.doHandleRequest(getMBeanServerManager(connection),request);
    }

    private JmxNotificationRequest createRequest() throws MalformedObjectNameException {
        return new JmxRequestBuilder(RequestType.NOTIFICATION).
                command(NotificationCommandType.REGISTER).build();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testUnsupported() throws Exception {
        NotificationHandler handler = new NotificationHandler(new AllowAllRestrictor());
        JmxNotificationRequest request = createRequest();
        assertTrue(handler.handleAllServersAtOnce(request));
        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        handler.doHandleRequest(connection,request);
    }
}
