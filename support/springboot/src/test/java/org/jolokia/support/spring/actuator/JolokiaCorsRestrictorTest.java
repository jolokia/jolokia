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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.management.ObjectName;

import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.support.spring.actuator.JolokiaProperties.CorsConfiguration;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.easymock.EasyMock.*;

public class JolokiaCorsRestrictorTest {

    @Test
    public void preservesNonCorsChecks() throws Exception {
        ObjectName name = new ObjectName("test:type=Restricted");
        for (boolean allowed : new boolean[] {false, true}) {
            Restrictor delegate = createMock(Restrictor.class);
            expect(delegate.isHttpMethodAllowed(HttpMethod.POST)).andReturn(allowed);
            expect(delegate.isCorsMethodAllowed(HttpMethod.POST)).andReturn(allowed);
            expect(delegate.isTypeAllowed(RequestType.EXEC)).andReturn(allowed);
            expect(delegate.isAttributeReadAllowed(name, "Value")).andReturn(allowed);
            expect(delegate.isAttributeWriteAllowed(name, "Value")).andReturn(allowed);
            expect(delegate.isOperationAllowed(name, "reset")).andReturn(allowed);
            expect(delegate.isRemoteAccessAllowed("host", "127.0.0.1")).andReturn(allowed);
            expect(delegate.isObjectNameHidden(name)).andReturn(allowed);
            replay(delegate);
            Restrictor policy = new JolokiaCorsRestrictor(
                new CorsConfiguration(List.of("http://override.example"), true, true), delegate);
            assertEquals(policy.isHttpMethodAllowed(HttpMethod.POST), allowed);
            assertEquals(policy.isCorsMethodAllowed(HttpMethod.POST), allowed);
            assertEquals(policy.isTypeAllowed(RequestType.EXEC), allowed);
            assertEquals(policy.isAttributeReadAllowed(name, "Value"), allowed);
            assertEquals(policy.isAttributeWriteAllowed(name, "Value"), allowed);
            assertEquals(policy.isOperationAllowed(name, "reset"), allowed);
            assertEquals(policy.isRemoteAccessAllowed("host", "127.0.0.1"), allowed);
            assertEquals(policy.isObjectNameHidden(name), allowed);
            assertTrue(policy.isOriginAllowed("http://override.example", true));
            assertFalse(policy.isOriginAllowed("http://denied.example", true));
            assertTrue(policy.ignoreScheme());
            verify(delegate);
        }
    }

    @Test
    public void programmaticPolicyMatchesXml() {
        CorsConfiguration config = new CorsConfiguration(List.of(" https://*.example.com ", "http://[::1]:8080",
            "https://example.com/?x=1&y=<test>", "https://例.example", "http://example.com/a,b"), true, true);
        JolokiaCorsRestrictor actual = new JolokiaCorsRestrictor(config, new AllowAllRestrictor());
        PolicyRestrictor xml = new PolicyRestrictor(new ByteArrayInputStream(("<access><cors>"
            + "<allow-origin>https://*.example.com</allow-origin><allow-origin>http://[::1]:8080</allow-origin>"
            + "<allow-origin>https://example.com/?x=1&amp;y=&lt;test&gt;</allow-origin>"
            + "<allow-origin>https://例.example</allow-origin><allow-origin>http://example.com/a,b</allow-origin>"
            + "<strict-checking/><ignore-scheme/></cors></access>").getBytes(StandardCharsets.UTF_8)));
        for (String origin : Arrays.asList(null, "null", "https://a.example.com", "https://a.example.com:443",
                "http://a.example.com", "http://[::1]:8080", "http://[::1]:8081",
                "https://example.com/?x=1&y=<test>", "https://例.example", "http://example.com/a,b", "http://denied.example")) {
            for (boolean strictOnly : new boolean[] {false, true}) {
                assertEquals(actual.isOriginAllowed(origin, strictOnly), xml.isOriginAllowed(origin, strictOnly),
                    "Origin: " + origin + ", strictOnly: " + strictOnly);
            }
        }
        assertEquals(actual.ignoreScheme(), xml.ignoreScheme());
    }
}
