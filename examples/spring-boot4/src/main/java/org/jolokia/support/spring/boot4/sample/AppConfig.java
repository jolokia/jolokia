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
package org.jolokia.support.spring.boot4.sample;

import java.util.Map;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.http.AgentServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ServletRegistrationBean<AgentServlet> jolokia() {
        ServletRegistrationBean<AgentServlet> jolokiaServlet = new ServletRegistrationBean<>(new AgentServlet(), "/jolokia/*");
        jolokiaServlet.setLoadOnStartup(0);
        jolokiaServlet.setAsyncSupported(true);
        jolokiaServlet.setInitParameters(Map.of(ConfigKey.DEBUG.getKeyValue(), "true"));
        jolokiaServlet.setInitParameters(Map.of(ConfigKey.AGENT_DESCRIPTION.getKeyValue(), "Spring Servlet Jolokia Agent"));
        return jolokiaServlet;
    }

}
