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

import org.jolokia.server.core.http.AgentServlet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * This autoconfiguration ensures that we can see {@code /jolokia} link under {@link /actuator} page.
 * We don't declare any {@link Bean} that registers servlet or controller.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(AgentServlet.class)
public class JolokiaWebEndpointAutoConfiguration {

    @Bean
    public JolokiaWebEndpoint jolokiaManagementEndpoint() {
        return new JolokiaWebEndpoint();
    }

}
