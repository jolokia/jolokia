package org.jolokia.jvmagent.spring;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.jolokia.Version;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 29.12.12
 */
public class SpringJolokiaServerTest {

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
        Map<String,SpringJolokiaConfig> configs = new HashMap<String, SpringJolokiaConfig>();
        configs.put("B",getConfig(false,10,"executor","single","agentContext","/j4p/"));
        configs.put("A", getConfig(true, 20, "executor", "fixed", "threadNr", "2"));
        expect(ctx.getBeansOfType(SpringJolokiaConfig.class)).andReturn(configs);
        replay(ctx);
        server.setApplicationContext(ctx);
        server.afterPropertiesSet();
        JolokiaServerConfig cfg = server.getConfig();
        assertEquals(cfg.getExecutor(),"fixed");
        assertEquals(cfg.getThreadNr(),2);
        assertEquals(cfg.getContextPath(),"/j4p/");
        checkServerAndStop(server);
    }

    private SpringJolokiaConfig getConfig(boolean autoStart, int order, String ... extraArgs) throws IOException {
        SpringJolokiaConfig cfg = new SpringJolokiaConfig();
        cfg.setOrder(order);
        Map<String, String> map = new HashMap<String, String>();
        map.put("autoStart","" + autoStart);
        map.put("port", "" + EnvTestUtil.getFreePort());
        map.put("host","0.0.0.0");
        for (int i = 0; i < extraArgs.length; i+=2) {
            map.put(extraArgs[i],extraArgs[i+1]);
        }
        cfg.setConfig(map);
        return cfg;
    }

    private void checkServerAndStop(SpringJolokiaServer server) throws Exception {
        //Thread.sleep(2000);
        try {
            URL url = new URL(server.getUrl());
            String resp = EnvTestUtil.readToString(url.openStream());
            assertTrue(resp.matches(".*type.*version.*" + Version.getAgentVersion() + ".*"));
        } finally {
            server.destroy();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }
        }
    }
}
