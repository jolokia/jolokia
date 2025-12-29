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
import org.jolokia.server.core.service.api.Restrictor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.context.ApplicationContext;

/**
 * {@link ServletContextInitializer} (which runs in {@link jakarta.servlet.ServletContainerInitializer}) that
 * registers {@link AgentServlet} in management web context.
 */
public class JolokiaServletRegistration implements ServletContextInitializer {

    private final Map<String, String> initParameters;
    private final DispatcherServletPath dispatcherServletPath;
    private final WebEndpointProperties webEndpointProperties;
    private final ApplicationContext applicationContext;

    public JolokiaServletRegistration(Map<String, String> initParameters,
                                      WebEndpointProperties webEndpointProperties, DispatcherServletPath dispatcherServletPath, ApplicationContext applicationContext) {
        this.initParameters = initParameters;
        this.dispatcherServletPath = dispatcherServletPath;
        this.webEndpointProperties = webEndpointProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        // we can check
        // webEndpointProperties.getExposure().getInclude().contains("jolokia")
        // but it's enough to have @ConditionalOnAvailableEndpoint(JolokiaWebEndpoint.class) on this @Bean's
        // containing @ManagementContextConfiguration

        // we don't have to check the context path (which is configured differently for main and management contexts)
        // because servlet registration is done within single ServletContext
        // org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath.getPrefix() trims trailing "/"
        String prefix = dispatcherServletPath.getPrefix();

        // management.endpoints.web.base-path - defaults to /actuator and never ends with "/"
        // even if you set it to "/"
        String endpointsBasePath = webEndpointProperties.getBasePath();

        String jolokiaPath = "jolokia";
        Map<String, String> mapping = webEndpointProperties.getPathMapping();
        if (mapping.containsKey(jolokiaPath)) {
            jolokiaPath = mapping.get(jolokiaPath);
        }

        // https://github.com/jolokia/jolokia/issues/823
        // restrictors can be taken from ApplicationContext (including its parent(s)) and Agent servlet will
        // decide which one to use (it'll have access to LogHandler, so we're not logging anything here)
        Map<String, Restrictor> restrictors = BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, Restrictor.class);
        AgentServlet servlet = new AgentServlet();
        servlet.getInitRestrictors().putAll(restrictors);

        ServletRegistration.Dynamic reg = servletContext.addServlet("jolokia", servlet);
        reg.setInitParameters(initParameters);
        reg.addMapping(prefix + endpointsBasePath + "/" + jolokiaPath + "/*");
    }

}
