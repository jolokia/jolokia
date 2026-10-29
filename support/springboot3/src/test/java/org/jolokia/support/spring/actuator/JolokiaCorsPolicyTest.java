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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jolokia.core.util.ClassUtil;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class JolokiaCorsPolicyTest {

    private static final String PREFIX = "management.endpoint.jolokia.config.";

    private Map<String, String> bind(Map<String, ?> values) {
        return new Binder(new MapConfigurationPropertySource(values))
            .bind("management.endpoint.jolokia", Bindable.of(JolokiaProperties.class))
            .orElseGet(JolokiaProperties::new).getConfig();
    }

    private PolicyRestrictor policy(Map<String, String> config) {
        return JolokiaCorsPolicy.create(config, new StaticConfiguration());
    }

    @Test
    public void absentAndExplicitFalse() {
        assertFalse(JolokiaCorsPolicy.isConfigured(bind(Map.of(PREFIX + "debug", "true"))));
        Map<String, String> config = bind(Map.of(PREFIX + "strict-checking", "false", PREFIX + "ignore-scheme", "false"));
        assertTrue(JolokiaCorsPolicy.isConfigured(config));
        assertTrue(policy(config).isOriginAllowed(null, true));
        assertFalse(policy(config).ignoreScheme());
        assertTrue(policy(Map.of("ignore-scheme", " TRUE ")).ignoreScheme());
    }

    @Test
    public void commaSeparatedOriginsMatchXml() {
        Map<String, String> config = bind(Map.of(
            PREFIX + "allow-origin", " https://*.example.com ,http://[::1]:8080,https://example.com/?x=1&y=<test>,https://例.example",
            PREFIX + "strict-checking", "true", PREFIX + "ignore-scheme", "true"));
        PolicyRestrictor actual = policy(config);
        PolicyRestrictor xml = new PolicyRestrictor(new ByteArrayInputStream(("<access><cors>"
            + "<allow-origin>https://*.example.com</allow-origin>"
            + "<allow-origin>http://[::1]:8080</allow-origin>"
            + "<allow-origin>https://example.com/?x=1&amp;y=&lt;test&gt;</allow-origin>"
            + "<allow-origin>https://例.example</allow-origin>"
            + "<strict-checking/><ignore-scheme/></cors></access>").getBytes(StandardCharsets.UTF_8)));
        for (String origin : Arrays.asList(null, "null", "https://a.example.com", "https://a.example.com:443",
                "http://a.example.com", "http://[::1]:8080", "http://[::1]:8081",
                "https://example.com/?x=1&y=<test>", "https://例.example", "https://denied.example")) {
            for (boolean strictOnly : new boolean[] {false, true}) {
                assertEquals(actual.isOriginAllowed(origin, strictOnly), xml.isOriginAllowed(origin, strictOnly),
                    "Origin: " + origin + ", strictOnly: " + strictOnly);
            }
        }
        assertTrue(actual.ignoreScheme());
    }

    @Test
    public void emptyOriginsKeepXmlSemantics() {
        Map<String, String> config = bind(Map.of(PREFIX + "allow-origin", ""));
        assertTrue(JolokiaCorsPolicy.isConfigured(config));
        assertTrue(policy(config).isOriginAllowed("https://any.example", false));
        config.put("strict-checking", "true");
        assertTrue(policy(config).isOriginAllowed("https://any.example", false));
        assertFalse(policy(config).isOriginAllowed(null, true));
    }

    @Test
    public void invalidValuesAreRejected() {
        for (String key : List.of("strict-checking", "ignore-scheme")) {
            for (String value : List.of("perhaps", "", "1", "false,true")) {
                expectThrows(IllegalArgumentException.class, () -> policy(bind(Map.of(PREFIX + key, value))));
            }
        }
        for (String value : List.of(",", ",https://a.example", "https://a.example,", "https://a.example, ,https://b.example")) {
            expectThrows(IllegalArgumentException.class, () -> policy(Map.of("allow-origin", value)));
        }
        Map<String, String> config = new java.util.HashMap<>();
        config.put("allow-origin", null);
        expectThrows(IllegalArgumentException.class, () -> policy(config));
    }

    private Map<String, String> bindYaml(String config) throws Exception {
        String yaml = "management:\n  endpoint:\n    jolokia:\n      config: " + config;
        var sources = new YamlPropertySourceLoader().load("test",
            new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        return new Binder(ConfigurationPropertySources.from(sources))
            .bind("management.endpoint.jolokia", Bindable.of(JolokiaProperties.class))
            .orElseGet(JolokiaProperties::new).getConfig();
    }

    @Test
    public void yamlScalarsAndEmptyMap() throws Exception {
        assertFalse(JolokiaCorsPolicy.isConfigured(bindYaml("{}\n")));
        Map<String, String> empty = bindYaml("\n        allow-origin: []\n");
        assertTrue(JolokiaCorsPolicy.isConfigured(empty));
        assertTrue(policy(empty).isOriginAllowed("http://any.example", false));
        Map<String, String> config = bindYaml("\n        allow-origin: 'http://a.example, http://b.example'\n"
            + "        strict-checking: true\n        ignore-scheme: false\n");
        assertTrue(policy(config).isOriginAllowed("http://a.example", true));
        assertTrue(policy(config).isOriginAllowed("http://b.example", true));
        assertFalse(policy(config).isOriginAllowed("http://denied.example", true));
        assertFalse(policy(config).ignoreScheme());
        assertTrue(JolokiaCorsPolicy.isConfigured(bindYaml("\n        strict-checking: false\n")));
    }

    @Test
    public void indexedOptionsAreRejectedInsteadOfIgnored() throws Exception {
        Map<String, String> config = bindYaml("\n        allow-origin:\n          - http://a.example\n");
        assertTrue(JolokiaCorsPolicy.isConfigured(config));
        expectThrows(IllegalArgumentException.class, () -> policy(config));
        Map<String, String> indexed = bind(Map.of(PREFIX + "allow-origin[0]", "http://a.example"));
        assertTrue(JolokiaCorsPolicy.isConfigured(indexed));
        expectThrows(IllegalArgumentException.class, () -> policy(indexed));
        for (String option : List.of("strict-checking", "ignore-scheme")) {
            Map<String, String> invalid = bindYaml("\n        " + option + ": [true]\n");
            assertTrue(JolokiaCorsPolicy.isConfigured(invalid));
            expectThrows(IllegalArgumentException.class, () -> policy(invalid));
        }
    }

    @Test
    public void explicitPolicySourcesConflictWithoutLoadingThem() {
        for (ConfigKey key : List.of(ConfigKey.POLICY_LOCATION, ConfigKey.RESTRICTOR_CLASS)) {
            IllegalStateException error = expectThrows(IllegalStateException.class, () -> JolokiaCorsPolicy.create(
                Map.of("strict-checking", "false"), new StaticConfiguration(key, "https://must-not-be-fetched.invalid/policy")));
            assertTrue(error.getMessage().contains("cannot be combined"));
        }
    }

    @Test
    public void systemPropertyPolicyConflicts() {
        String key = ConfigKey.POLICY_LOCATION.asSystemProperty();
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "classpath:/another-policy.xml");
            expectThrows(IllegalStateException.class,
                () -> JolokiaCorsPolicy.create(Map.of("strict-checking", "false"), new StaticConfiguration()));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    public void environmentPolicyConflicts() {
        StaticConfiguration configuration = new StaticConfiguration() {
            @Override
            protected Map<String, String> env() {
                return Map.of(ConfigKey.POLICY_LOCATION.asEnvVariable(), "classpath:/environment-policy.xml");
            }
        };
        expectThrows(IllegalStateException.class,
            () -> JolokiaCorsPolicy.create(Map.of("strict-checking", "false"), configuration));
    }

    @Test
    public void defaultPolicyFromContextClassLoaderConflictsAndIsClosed() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        boolean[] closed = {false};
        ClassLoader parent = new ClassLoader(ClassUtil.class.getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("/jolokia-access.xml".equals(name)) {
                    return new ByteArrayInputStream("<access/>".getBytes(StandardCharsets.UTF_8)) {
                        @Override
                        public void close() {
                            closed[0] = true;
                        }
                    };
                }
                return super.getResourceAsStream(name);
            }
        };
        try {
            Thread.currentThread().setContextClassLoader(parent);
            expectThrows(IllegalStateException.class,
                () -> JolokiaCorsPolicy.create(Map.of("strict-checking", "false"), new StaticConfiguration()));
            assertTrue(closed[0]);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
