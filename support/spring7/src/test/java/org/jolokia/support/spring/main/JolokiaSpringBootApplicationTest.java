/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.support.spring.main;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class JolokiaSpringBootApplicationTest {

    @Test
    public void runSpringBootApplication() throws IOException {
        ConfigurableApplicationContext context = SpringApplication.run(Zero.class);

        assertNotNull(context.getBean(WebServerFactory.class));

        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            props.load(is);
        }

        // https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.server.server.port
        int serverPort = Integer.parseInt(props.getProperty("server.port", "8080"));
        // https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.actuator.management.server.port
        int managementPort = Integer.parseInt(props.getProperty("management.server.port", Integer.toString(serverPort)));

        String jolokiaPath = props.getProperty("management.endpoints.web.path-mapping.jolokia", "jolokia");

        URL jolokiaVersion = null;

        if (serverPort == managementPort) {
            // there's one org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
            //  - we can NOT configure management web context base path with management.server.base-path
            //  - we can configure main (only) web context base path with server.servlet.context-path
            //  - we can configure actuator base path (under server.servlet.context-path) with management.endpoints.web.base-path
            //    (defaults to "/actuator")
            //  - Jolokia actuator will be available at http://localhost:<server.port>/<server.servlet.context-path><spring.mvc.servlet.path><management.server.base-path>
            String contextPath = props.getProperty("server.servlet.context-path", "");
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            String servletPath = props.getProperty("spring.mvc.servlet.path", "");
            if ("/".equals(servletPath)) {
                servletPath = "";
            }
            String actuatorPath = props.getProperty("management.endpoints.web.base-path", "/actuator");
            if ("/".equals(actuatorPath)) {
                actuatorPath = "";
            }
            jolokiaVersion = new URL("http://localhost:" + serverPort + contextPath + servletPath + actuatorPath + "/" + jolokiaPath);
        } else {
            // there are two org.springframework.boot.web.servlet.context.ServletWebServerApplicationContexts
            //  - we can configure management web context base path with management.server.base-path
            //    (defaults to "" == "/")
            //  - we can configure actuator base path (under management.server.base-path) with management.endpoints.web.base-path
            //    (defaults to "/actuator")
            //  - Jolokia actuator will be available at http://localhost:<management.server.port>/<management.server.base-path><management.server.base-path>
            String contextPath = props.getProperty("management.server.base-path", "");
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            String actuatorPath = props.getProperty("management.endpoints.web.base-path", "/actuator");
            if ("/".equals(actuatorPath)) {
                actuatorPath = "";
            }
            jolokiaVersion = new URL("http://localhost:" + managementPort + contextPath + actuatorPath + "/" + jolokiaPath);
        }

        System.out.println("Connecting to " + jolokiaVersion.toExternalForm());

        URLConnection con = jolokiaVersion.openConnection();
        if (con instanceof HttpURLConnection http) {
            assertEquals(http.getResponseCode(), 200);
        } else {
            fail("Can't open HTTP connection to Jolokia actuator endpoint");
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class Zero {
    }

}
