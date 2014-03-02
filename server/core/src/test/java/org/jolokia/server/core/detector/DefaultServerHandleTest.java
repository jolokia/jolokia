package org.jolokia.server.core.detector;

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

import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 06.06.12
 */
public class DefaultServerHandleTest {


    private DefaultServerHandle serverHandle;
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
        serverHandle = new DefaultServerHandle(vendor, product, version) {
            @Override
            public Map<String, String> getExtraInfo(MBeanServerAccess pServerManager) {
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
        ServerHandle handle = DefaultServerHandle.NULL_SERVER_HANDLE;
        assertNull(handle.getVendor());
        assertNull(handle.getProduct());
        assertNull(handle.getVersion());
    }
}
