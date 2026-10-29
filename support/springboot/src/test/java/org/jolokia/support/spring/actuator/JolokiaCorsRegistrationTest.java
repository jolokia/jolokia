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
import java.util.Map;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.easymock.Capture;
import org.jolokia.server.core.restrictor.DenyAllRestrictor;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.context.support.StaticApplicationContext;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

public class JolokiaCorsRegistrationTest {

    @Test
    public void parentBeanWinsBeforePolicyConflictChecks() {
        try (StaticApplicationContext parent = new StaticApplicationContext();
             StaticApplicationContext child = new StaticApplicationContext(parent)) {
            DenyAllRestrictor custom = new DenyAllRestrictor();
            parent.getBeanFactory().registerSingleton("customRestrictor", custom);
            ServletContext context = createMock(ServletContext.class);
            ServletRegistration.Dynamic registration = createMock(ServletRegistration.Dynamic.class);
            Capture<Servlet> servlet = newCapture();
            expect(context.addServlet(eq("jolokia"), capture(servlet))).andReturn(registration);
            Map<String, String> config = Map.of("policyLocation", "https://must-not-be-fetched.invalid/policy", "strict-checking", "invalid-but-ignored");
            expect(registration.setInitParameters(config)).andReturn(Collections.emptySet());
            expect(registration.addMapping("/actuator/jolokia/*")).andReturn(Collections.emptySet());
            replay(context, registration);
            new JolokiaServletRegistration(config, new WebEndpointProperties(), () -> "/", child).onStartup(context);
            assertSame(((org.jolokia.server.core.http.AgentServlet) servlet.getValue())
                .getInitRestrictors().get("customRestrictor"), custom);
            verify(context, registration);
        }
    }

    @Test
    public void contextPolicyConflictsBeforeServletRegistration() {
        try (StaticApplicationContext application = new StaticApplicationContext()) {
            ServletContext context = createMock(ServletContext.class);
            expect(context.getInitParameterNames()).andReturn(Collections.enumeration(java.util.List.of("policyLocation")));
            expect(context.getInitParameter("policyLocation")).andReturn("classpath:/legacy.xml");
            replay(context);
            expectThrows(IllegalStateException.class, () -> new JolokiaServletRegistration(Map.of("strict-checking", "false"),
                new WebEndpointProperties(), () -> "/", application).onStartup(context));
            verify(context);
        }
    }

    @Test
    public void absentCorsKeepsLegacyRegistration() {
        try (StaticApplicationContext application = new StaticApplicationContext()) {
            ServletContext context = createMock(ServletContext.class);
            ServletRegistration.Dynamic registration = createMock(ServletRegistration.Dynamic.class);
            expect(context.addServlet(eq("jolokia"), anyObject(Servlet.class))).andReturn(registration);
            Map<String, String> config = Map.of("policyLocation", "classpath:/legacy.xml");
            expect(registration.setInitParameters(config)).andReturn(Collections.emptySet());
            expect(registration.addMapping("/actuator/jolokia/*")).andReturn(Collections.emptySet());
            replay(context, registration);
            JolokiaServletRegistration initializer = new JolokiaServletRegistration(config,
                new WebEndpointProperties(), () -> "/", application);
            initializer.onStartup(context);
            verify(context, registration);
        }
    }
}
