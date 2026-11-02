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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.support.spring.actuator.JolokiaProperties.CorsConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class JolokiaCorsConfigurationTest {

    private static final String PREFIX = "management.endpoint.jolokia.cors.";

    private JolokiaProperties bind(Map<String, ?> values) {
        return new Binder(new MapConfigurationPropertySource(values))
            .bind("management.endpoint.jolokia", Bindable.of(JolokiaProperties.class))
            .orElseGet(JolokiaProperties::new);
    }

    private JolokiaProperties bindYaml(String cors) throws Exception {
        String yaml = "management:\n  endpoint:\n    jolokia:\n      cors: " + cors;
        var sources = new YamlPropertySourceLoader().load("test",
            new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        return new Binder(ConfigurationPropertySources.from(sources))
            .bind("management.endpoint.jolokia", Bindable.of(JolokiaProperties.class))
            .orElseGet(JolokiaProperties::new);
    }

    @Test
    public void absentCorsAndExplicitFalse() {
        JolokiaProperties legacy = bind(Map.of("management.endpoint.jolokia.config.debug", "true"));
        assertNull(legacy.getCors());
        assertEquals(legacy.getConfig().get("debug"), "true");
        CorsConfiguration cors = bind(Map.of(PREFIX + "strict-checking", "false")).getCors();
        assertNotNull(cors);
        assertFalse(cors.strictChecking());
        assertFalse(cors.ignoreScheme());
        assertTrue(cors.allowOrigin().isEmpty());
    }

    @Test
    public void yamlListsAndScalars() throws Exception {
        CorsConfiguration cors = bindYaml("\n        allow-origin:\n"
            + "          - 'http://a.example'\n          - 'http://b.example/path,with-comma'\n"
            + "        strict-checking: true\n        ignore-scheme: true\n").getCors();
        assertEquals(cors.allowOrigin(), List.of("http://a.example", "http://b.example/path,with-comma"));
        assertTrue(cors.strictChecking());
        assertTrue(cors.ignoreScheme());
        assertTrue(new JolokiaCorsRestrictor(cors, new AllowAllRestrictor()).isOriginAllowed("http://b.example/path,with-comma", true));
        assertFalse(new JolokiaCorsRestrictor(cors, new AllowAllRestrictor()).isOriginAllowed("http://denied.example", true));
        CorsConfiguration scalar = bind(Map.of(PREFIX + "allow-origin", "http://a.example,http://b.example")).getCors();
        assertEquals(scalar.allowOrigin(), List.of("http://a.example", "http://b.example"));
        assertTrue(bindYaml("\n        allow-origin: []\n").getCors().allowOrigin().isEmpty());
    }

    @Test
    public void indexedPropertiesAndRelaxedBinding() {
        CorsConfiguration cors = bind(Map.of(PREFIX + "allow-origin[0]", "http://a.example",
            PREFIX + "allow-origin[1]", "http://b.example", PREFIX + "strictChecking", "true")).getCors();
        assertEquals(cors.allowOrigin(), List.of("http://a.example", "http://b.example"));
        assertTrue(cors.strictChecking());
    }

    @Test
    public void invalidValuesFail() throws Exception {
        for (String option : List.of("strict-checking", "ignore-scheme")) {
            expectThrows(BindException.class, () -> bind(Map.of(PREFIX + option, "perhaps")));
        }
        CorsConfiguration cors = bindYaml("\n        allow-origin: ['http://a.example', ' ']\n").getCors();
        expectThrows(IllegalArgumentException.class, () -> new JolokiaCorsRestrictor(cors, new AllowAllRestrictor()));
    }

    @Test
    public void higherPrioritySourceReplacesListAndBoolean() {
        var high = new MapConfigurationPropertySource(Map.of(PREFIX + "allow-origin[0]", "http://override.example",
            PREFIX + "strict-checking", "false"));
        var low = new MapConfigurationPropertySource(Map.of(PREFIX + "allow-origin[0]", "http://a.example",
            PREFIX + "allow-origin[1]", "http://b.example", PREFIX + "strict-checking", "true"));
        CorsConfiguration cors = new Binder(high, low)
            .bind("management.endpoint.jolokia", Bindable.of(JolokiaProperties.class)).get().getCors();
        assertEquals(cors.allowOrigin(), List.of("http://override.example"));
        assertFalse(cors.strictChecking());
    }
}
