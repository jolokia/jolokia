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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class JolokiaCorsHttpTest {

    private static final String PREFIX = "--management.endpoint.jolokia.config.";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DataProvider
    public Object[][] ports() {
        return new Object[][] {{false}, {true}};
    }

    private ConfigurableApplicationContext start(boolean separatePort, String... properties) {
        List<String> args = new ArrayList<>(List.of(
            "--spring.config.location=optional:classpath:/no-cors-test-defaults.properties",
            "--spring.main.banner-mode=off", "--server.address=127.0.0.1",
            "--server.port=" + (separatePort ? "-1" : "0"),
            "--management.endpoints.web.exposure.include=jolokia",
            "--management.endpoint.jolokia.config.discoveryEnabled=false"));
        if (separatePort) {
            args.add("--management.server.port=0");
            args.add("--management.server.address=127.0.0.1");
        }
        for (String property : properties) {
            String name = property.substring(0, property.indexOf('=') + 1);
            args.removeIf(argument -> argument.startsWith(name));
            args.add(property);
        }
        return SpringApplication.run(EndpointApplication.class, args.toArray(String[]::new));
    }

    private HttpResponse<String> request(ConfigurableApplicationContext context, String method, String origin,
                                         String... headers) throws Exception {
        String port = context.getEnvironment().getProperty("local.management.port",
            context.getEnvironment().getProperty("local.server.port"));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/jolokia"))
            .timeout(Duration.ofSeconds(10));
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        if ("OPTIONS".equals(method)) {
            request.header("Access-Control-Request-Method", "POST");
        }
        request.method(method, "POST".equals(method)
            ? HttpRequest.BodyPublishers.ofString("{\"type\":\"version\"}") : HttpRequest.BodyPublishers.noBody());
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertJolokiaStatus(HttpResponse<String> response, int status) throws Exception {
        assertEquals(response.statusCode(), 200, response.body());
        JSONObject body = new JSONParser().parse(response.body(), JSONObject.class);
        assertEquals(((Number) body.get("status")).intValue(), status, response.body());
    }

    @Test(dataProvider = "ports")
    public void strictCorsOverHttp(boolean separatePort) throws Exception {
        try (ConfigurableApplicationContext context = start(separatePort,
                PREFIX + "allow-origin=http://allowed.example",
                PREFIX + "strict-checking=true")) {
            for (String method : List.of("GET", "POST")) {
                HttpResponse<String> allowed = request(context, method, "http://allowed.example");
                assertJolokiaStatus(allowed, 200);
                assertEquals(allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(null), "http://allowed.example");
                HttpResponse<String> denied = request(context, method, "http://denied.example");
                assertJolokiaStatus(denied, 403);
                assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            }
            HttpResponse<String> preflight = request(context, "OPTIONS", "http://allowed.example");
            assertEquals(preflight.statusCode(), 204);
            assertEquals(preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(null), "http://allowed.example");
            assertTrue(preflight.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
            assertJolokiaStatus(request(context, "OPTIONS", "http://denied.example"), 403);
            assertJolokiaStatus(request(context, "GET", null), 403);
            assertJolokiaStatus(request(context, "GET", "null"), 403);
            assertJolokiaStatus(request(context, "GET", null, "Referer", "http://allowed.example"), 200);
        }
    }

    @Test
    public void nonStrictOnlyControlsCorsHeaders() throws Exception {
        try (ConfigurableApplicationContext context = start(false, PREFIX + "allow-origin=http://allowed.example",
                PREFIX + "strict-checking=false")) {
            assertJolokiaStatus(request(context, "GET", null), 200);
            HttpResponse<String> denied = request(context, "GET", "http://denied.example");
            assertJolokiaStatus(denied, 200);
            assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            HttpResponse<String> preflight = request(context, "OPTIONS", "http://denied.example");
            assertEquals(preflight.statusCode(), 204);
            assertTrue(preflight.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        }
    }

    @Test
    public void ignoreSchemePreservesAgentSemantics() throws Exception {
        for (boolean ignore : new boolean[] {false, true}) {
            try (ConfigurableApplicationContext context = start(false, PREFIX + "allow-origin=https://allowed.example",
                    PREFIX + "strict-checking=true", PREFIX + "ignore-scheme=" + ignore)) {
                assertJolokiaStatus(request(context, "GET", "https://allowed.example"), ignore ? 200 : 403);
            }
        }
    }

    @Test
    public void yamlAndLegacyXmlPolicies() throws Exception {
        try (ConfigurableApplicationContext context = start(false, "--spring.config.location=classpath:/cors-policy.yml")) {
            assertJolokiaStatus(request(context, "GET", "http://allowed.example"), 200);
            assertJolokiaStatus(request(context, "GET", "http://denied.example"), 403);
        }
        try (ConfigurableApplicationContext context = start(false,
                "--management.endpoint.jolokia.config.policyLocation=classpath:/cors-policy.xml")) {
            assertJolokiaStatus(request(context, "GET", "http://allowed.example"), 200);
            assertJolokiaStatus(request(context, "GET", "http://denied.example"), 403);
        }
    }

    @Test
    public void conflictPreventsStartup() {
        RuntimeException error = expectThrows(RuntimeException.class, () -> {
            try (ConfigurableApplicationContext ignored = start(false, PREFIX + "strict-checking=false",
                    "--management.endpoint.jolokia.config.policyLocation=classpath:/cors-policy.xml")) {
                fail("Conflicting policies must prevent startup");
            }
        });
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().contains("cannot be combined"), error.toString());
    }

    @Test
    public void unexposedEndpointDoesNotCreatePolicy() throws Exception {
        try (ConfigurableApplicationContext context = start(false, PREFIX + "strict-checking=true",
                "--management.endpoint.jolokia.config.policyLocation=classpath:/cors-policy.xml",
                "--management.endpoints.web.exposure.include=health")) {
            assertEquals(request(context, "GET", null).statusCode(), 404);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class EndpointApplication {
    }
}
