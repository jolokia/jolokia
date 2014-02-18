package org.jolokia.detector;

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

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.ConfigKey;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.LogHandler;
import org.jolokia.util.TestJolokiaContext;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 06.06.12
 */
public class ServerHandleTest {


    private ServerHandle serverHandle;
    private String vendor;
    private String product;
    private String version;
    private Map<String,String> extraInfo;

    @BeforeMethod
    public void setup() throws MalformedURLException {
        extraInfo = new HashMap<String, String>();
        extraInfo.put("extra1", "value1");
        vendor = "acim";
        product = "dukeNukem";
        version = "forEver";
        serverHandle = new ServerHandle(vendor, product, version) {
            @Override
            public Map<String, String> getExtraInfo(MBeanServerExecutor pServerManager) {
                return extraInfo;
            }
        };
    }

    @Test
    public void basics() throws MalformedURLException {
        assertEquals(serverHandle.getProduct(),product);
        assertEquals(serverHandle.getVendor(),vendor);
        assertEquals(serverHandle.getExtraInfo(null).get("extra1"),"value1");
        assertEquals(serverHandle.getVersion(),version);
    }

    @Test
    public void toJson() {
        JSONObject json = serverHandle.toJSONObject();
        assertEquals(json.get("vendor"),vendor);
        assertEquals(json.get("product"),product);
        assertEquals(json.get("version"),version);
    }

    @Test
    public void allNull() {
        ServerHandle handle = ServerHandle.NULL_SERVER_HANDLE;
        assertNull(handle.getVendor());
        assertNull(handle.toJSONObject().get("extraInfo"));
    }

    @Test
    public void detectorOptions() {
        JolokiaContext ctx = new TestJolokiaContext.Builder().config(ConfigKey.DETECTOR_OPTIONS, "{\"dukeNukem\" : {\"doIt\" : true }}").build();
        JSONObject config = serverHandle.getDetectorOptions(ctx);
        assertTrue((Boolean) config.get("doIt"));
    }

    @Test
    public void detectorOptionsEmpty() {
        JSONObject config = serverHandle.getDetectorOptions(new TestJolokiaContext());
        assertNull(config);
    }

    @Test
    public void detectOptionsFail() {
        LogHandler handler = EasyMock.createMock(LogHandler.class);
        handler.error(matches("^.*parse options.*"),isA(Exception.class));
        replay(handler);

        JolokiaContext opts = new TestJolokiaContext.Builder()
                .config(ConfigKey.DETECTOR_OPTIONS,"blub: bla")
                .logHandler(handler)
                .build();
        JSONObject config = serverHandle.getDetectorOptions(opts);
        assertNull(config);
        verify(handler);
    }
}
