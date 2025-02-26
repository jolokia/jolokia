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

import jakarta.servlet.Servlet;
import org.jolokia.server.core.http.AgentServlet;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * This autoconfiguration is for "management context" and provides a {@link AgentServlet Jolokia Servlet} registration
 * {@link Bean} that simply calls {@link jakarta.servlet.ServletContext#addServlet}.
 */
@ManagementContextConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(AgentServlet.class)
@ConditionalOnAvailableEndpoint(JolokiaWebEndpoint.class)
@EnableConfigurationProperties(JolokiaProperties.class)
public class JolokiaServletAutoConfiguration {

    @Bean
    public JolokiaServletRegistration jolokiaServletRegistration(JolokiaProperties properties,
                                                      WebEndpointProperties webEndpointProperties, DispatcherServletPath dispatcherServletPath) {
        return new JolokiaServletRegistration(properties.getConfig(), webEndpointProperties, dispatcherServletPath);
    }

}
