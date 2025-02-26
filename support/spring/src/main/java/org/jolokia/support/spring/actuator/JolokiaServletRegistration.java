/*
 * Copyright 2009-2023 Roland Huss
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

import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.jolokia.server.core.http.AgentServlet;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.web.servlet.ServletContextInitializer;

/**
 * {@link ServletContextInitializer} (which runs in {@link jakarta.servlet.ServletContainerInitializer}) that
 * registers {@link AgentServlet} in management web context.
 */
public class JolokiaServletRegistration implements ServletContextInitializer {

    private final Map<String, String> initParameters;
    private final DispatcherServletPath dispatcherServletPath;
    private final WebEndpointProperties webEndpointProperties;

    public JolokiaServletRegistration(Map<String, String> initParameters,
                                      WebEndpointProperties webEndpointProperties, DispatcherServletPath dispatcherServletPath) {
        this.initParameters = initParameters;
        this.dispatcherServletPath = dispatcherServletPath;
        this.webEndpointProperties = webEndpointProperties;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        // we can check
        // webEndpointProperties.getExposure().getInclude().contains("jolokia")
        // but it's enough to have @ConditionalOnAvailableEndpoint(JolokiaWebEndpoint.class) on this @Bean's
        // containing @ManagementContextConfiguration

        String basePath = dispatcherServletPath.getRelativePath(webEndpointProperties.getBasePath());
        ServletRegistration.Dynamic reg = servletContext.addServlet("jolokia", AgentServlet.class);
        reg.setInitParameters(initParameters);
        reg.addMapping(basePath + "/jolokia/*");
    }

}
