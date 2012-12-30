/*
 * Copyright 2009-2012  Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.jvmagent.spring;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 29.12.12
 */
public class SpringJolokiaServerTest extends BaseServerTest {

    @Test
    public void withoutStart() throws Exception {
        SpringJolokiaServer server = new SpringJolokiaServer();
        server.setConfig(getConfig(false,100));
        server.afterPropertiesSet();
        server.start();
        checkServerAndStop(server);
    }

    @Test
    public void withStart() throws Exception {
        SpringJolokiaServer server = new SpringJolokiaServer();
        server.setConfig(getConfig(true,100));
        server.afterPropertiesSet();
        checkServerAndStop(server);
    }

    @Test
    public void withMultiConfigAndStart() throws Exception {
        SpringJolokiaServer server = new SpringJolokiaServer();
        server.setLookupConfig(true);
        server.setConfig(getConfig(true,100));

        ApplicationContext ctx = createMock(ApplicationContext.class);
        Map<String,SpringJolokiaConfigWrapper> configs = new HashMap<String, SpringJolokiaConfigWrapper>();
        configs.put("B",getConfig(false,10,"executor","single","agentContext","/j4p/"));
        configs.put("A", getConfig(true, 20, "executor", "fixed", "threadNr", "2"));
        expect(ctx.getBeansOfType(SpringJolokiaConfigWrapper.class)).andReturn(configs);
        replay(ctx);
        server.setApplicationContext(ctx);
        server.afterPropertiesSet();
        JolokiaServerConfig cfg = server.getServerConfig();
        assertEquals(cfg.getExecutor(),"fixed");
        assertEquals(cfg.getThreadNr(),2);
        assertEquals(cfg.getContextPath(),"/j4p/");
        checkServerAndStop(server);
    }

    private SpringJolokiaConfigWrapper getConfig(boolean autoStart, int order, String ... extraArgs) throws IOException {
        SpringJolokiaConfigWrapper cfg = new SpringJolokiaConfigWrapper();
        cfg.setOrder(order);
        Map<String, String> map = new HashMap<String, String>();
        map.put("autoStart","" + autoStart);
        map.put("port", "" + EnvTestUtil.getFreePort());
        map.put("host","127.0.0.1");
        for (int i = 0; i < extraArgs.length; i+=2) {
            map.put(extraArgs[i],extraArgs[i+1]);
        }
        cfg.setConfig(map);
        return cfg;
    }

}
