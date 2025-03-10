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

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.web.servlet.ModelAndView;

/**
 * Jolokia actuator endpoint ({@code /actuator/jolokia}) which used to register a servlet, but now only
 * registers a link under {@code /actuator} listing page.
 */
@WebEndpoint(id = "jolokia")
public class JolokiaWebEndpoint {

    private final ManagementServerProperties managementServerProperties;
    private final WebEndpointProperties webEndpointProperties;
    private final DispatcherServletPath dispatcherServletPath;

    public JolokiaWebEndpoint(ManagementServerProperties managementServerProperties, WebEndpointProperties webEndpointProperties, DispatcherServletPath dispatcherServletPath) {
        this.managementServerProperties = managementServerProperties;
        this.webEndpointProperties = webEndpointProperties;
        this.dispatcherServletPath = dispatcherServletPath;
    }

    /**
     * This operation is not invoked, because actual {@link JolokiaServletRegistration Jolokia servlet registration}
     * is overriding {@code /actuator/jolokia/*} mapping (taking into account actuator base/context path
     * configuration). Only when Jolokia's servlet is not registered, this method will be used to redirect to
     * non-existing {@link /version} URL.
     * @return
     */
    @ReadOperation
    public ModelAndView jolokia() {
        // org.springframework.web.servlet.view.RedirectView which is used for "redirect:" is by default
        // context-relative, so we don't have to check what is:
        //  - server.servlet.context-path when there's one context
        //  - management.server.base-path when server.port is different than management.server.port

        // spring.mvc.servlet.path for "main" context and "/" for "management" context
        // but we don't have to guess which one to use - we access it through proper DispatcherServletPath
        String prefix = dispatcherServletPath.getPrefix();

        // management.endpoints.web.base-path - defaults to /actuator
        String endpointsBasePath = webEndpointProperties.getBasePath();

        String jolokiaPath = "jolokia";
        Map<String, String> mapping = webEndpointProperties.getPathMapping();
        if (mapping.containsKey(jolokiaPath)) {
            jolokiaPath = mapping.get(jolokiaPath);
        }

        return new ModelAndView("redirect:" + prefix + endpointsBasePath + "/" + jolokiaPath + "/version");
    }

}
