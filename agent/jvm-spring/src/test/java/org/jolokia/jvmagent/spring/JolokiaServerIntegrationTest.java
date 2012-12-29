package org.jolokia.jvmagent.spring;

import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 29.12.12
 */
public class JolokiaServerIntegrationTest extends BaseServerTest {

    @Test
    public void simple() throws Exception {
        System.setProperty("jolokia.port", "" + EnvTestUtil.getFreePort());
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/spring-jolokia-context.xml");
        SpringJolokiaServer server = (SpringJolokiaServer) ctx.getBean("jolokia");
        JolokiaServerConfig cfg = server.getServerConfig();
        assertEquals(cfg.getContextPath(),"/j4p/");
        checkServerAndStop(server);
    }
}
