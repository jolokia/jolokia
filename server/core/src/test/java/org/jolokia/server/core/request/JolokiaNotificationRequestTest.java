/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.server.core.request;

import java.util.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.notification.*;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 20.03.13
 */
public class JolokiaNotificationRequestTest {

    @Test
    public void testSimpleStack() throws Exception {
        RequestCreator<JolokiaNotificationRequest> creator = JolokiaNotificationRequest.newCreator();
        Deque<String> args = new LinkedList<>();
        args.push("register");
        JolokiaNotificationRequest request = creator.create(args,getParams());

        assertEquals(request.getType(), RequestType.NOTIFICATION);
        assertEquals(request.getHttpMethod(), HttpMethod.GET);
        NotificationCommand command = request.getCommand();
        assertEquals(command.getType(), NotificationCommandType.REGISTER);
    }

    @Test
    public void testSimpleMap() throws Exception {
        RequestCreator<JolokiaNotificationRequest> creator = JolokiaNotificationRequest.newCreator();
        Map<String,String> map = new HashMap<>();
        map.put("type","notification");
        map.put("command","ping");
        map.put("client","dummy");
        JolokiaNotificationRequest request = creator.create(map,getParams());

        assertEquals(request.getType(), RequestType.NOTIFICATION);
        assertEquals(request.getHttpMethod(), HttpMethod.POST);
        PingCommand command = request.getCommand();
        assertEquals(command.getType(), NotificationCommandType.PING);
        assertEquals(command.getClient(),"dummy");
    }

    @Test
    public void testToJson() throws Exception {
        RequestCreator<JolokiaNotificationRequest> creator = JolokiaNotificationRequest.newCreator();
        Map<String,Object> map = new HashMap<>();
        map.put("type","notification");
        map.put("command","add");
        map.put("client","dummy");
        map.put("mode","pull");
        map.put("mbean","test:type=test");
        map.put("filter",Arrays.asList("filter1","filter2"));
        map.put("config", new HashMap<>());
        JolokiaNotificationRequest request = creator.create(map,getParams());
        JSONObject ret = request.toJSON();
        assertEquals(ret.length(), 6);
        assertEquals(ret.get("mbean"),"test:type=test");
        List<?> filters = (List<?>) ret.get("filter");
        assertEquals(filters.size(),2);
        assertTrue(filters.contains("filter1"));
        assertFalse(ret.toMap().containsKey("config"));
    }

    private ProcessingParameters getParams() {
        return TestProcessingParameters.create(ConfigKey.MAX_DEPTH,"2");
    }
}
