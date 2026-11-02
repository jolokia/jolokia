/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.support.spring.actuator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.easymock.Capture;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.http.AgentServlet;
import org.jolokia.server.core.restrictor.DenyAllRestrictor;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.support.spring.actuator.JolokiaProperties.CorsConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.context.support.StaticApplicationContext;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

public class JolokiaCorsRegistrationTest {

    @Test
    public void externalCorsPreservesParentBeanRestrictions() throws Exception {
        try (StaticApplicationContext parent = new StaticApplicationContext();
             StaticApplicationContext child = new StaticApplicationContext(parent)) {
            DenyAllRestrictor custom = new DenyAllRestrictor();
            parent.getBeanFactory().registerSingleton("customRestrictor", custom);
            CorsConfiguration cors = new CorsConfiguration(List.of("http://allowed.example"), true, true);
            AgentServlet servlet = register(child, Map.of(), cors);
            var create = AgentServlet.class.getDeclaredMethod("createRestrictor", Configuration.class, LogHandler.class);
            create.setAccessible(true);
            Restrictor actual = (Restrictor) create.invoke(servlet, new StaticConfiguration(), createNiceMock(LogHandler.class));
            assertNotSame(actual, custom);
            assertTrue(actual.isOriginAllowed("http://allowed.example", true));
            assertFalse(actual.isOriginAllowed("http://denied.example", true));
            assertTrue(actual.ignoreScheme());
            assertFalse(actual.isHttpMethodAllowed(HttpMethod.GET));
            assertFalse(actual.isRemoteAccessAllowed("127.0.0.1"));
        }
    }

    @Test
    public void absentCorsKeepsLegacyRegistration() {
        try (StaticApplicationContext application = new StaticApplicationContext()) {
            AgentServlet servlet = register(application, Map.of("policyLocation", "classpath:/legacy.xml"), null);
            assertEquals(servlet.getClass(), AgentServlet.class);
        }
    }

    private AgentServlet register(StaticApplicationContext application, Map<String, String> config, CorsConfiguration cors) {
        ServletContext context = createMock(ServletContext.class);
        ServletRegistration.Dynamic registration = createMock(ServletRegistration.Dynamic.class);
        Capture<Servlet> servlet = newCapture();
        expect(context.addServlet(eq("jolokia"), capture(servlet))).andReturn(registration);
        expect(registration.setInitParameters(config)).andReturn(Collections.emptySet());
        expect(registration.addMapping("/actuator/jolokia/*")).andReturn(Collections.emptySet());
        replay(context, registration);
        JolokiaServletRegistration initializer = cors == null
            ? new JolokiaServletRegistration(config, new WebEndpointProperties(), () -> "/", application)
            : new JolokiaServletRegistration(config, cors, new WebEndpointProperties(), () -> "/", application);
        initializer.onStartup(context);
        verify(context, registration);
        return (AgentServlet) servlet.getValue();
    }
}
