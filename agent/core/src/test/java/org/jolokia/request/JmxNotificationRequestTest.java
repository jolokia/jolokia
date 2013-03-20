package org.jolokia.request;

import java.util.*;

import org.jolokia.config.Configuration;
import org.jolokia.config.ProcessingParameters;
import org.jolokia.request.notification.*;
import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 20.03.13
 */
public class JmxNotificationRequestTest {

    @Test
    public void testSimpleStack() throws Exception {
        RequestCreator<JmxNotificationRequest> creator = JmxNotificationRequest.newCreator();
        Stack<String> args = new Stack<String>();
        args.push("register");
        JmxNotificationRequest request = creator.create(args,getParams());

        assertEquals(request.getType(), RequestType.NOTIFICATION);
        assertEquals(request.getHttpMethod(), HttpMethod.GET);
        NotificationCommand command = request.getCommand();
        assertEquals(command.getType(), NotificationCommandType.REGISTER);
    }

    @Test
    public void testSimpleMap() throws Exception {
        RequestCreator<JmxNotificationRequest> creator = JmxNotificationRequest.newCreator();
        Map<String,String> map = new HashMap<String, String>();
        map.put("type","notification");
        map.put("command","ping");
        map.put("client","dummy");
        JmxNotificationRequest request = creator.create(map,getParams());

        assertEquals(request.getType(), RequestType.NOTIFICATION);
        assertEquals(request.getHttpMethod(), HttpMethod.POST);
        PingCommand command = request.getCommand();
        assertEquals(command.getType(), NotificationCommandType.PING);
        assertEquals(command.getClient(),"dummy");
    }

    private ProcessingParameters getParams() {
        Configuration config = new Configuration();
        Map params = new HashMap();
        params.put("maxDepth","2");
        return config.getProcessingParameters(params);
    }
}
