/*
 *
 * Copyright 2016 Roland Huss
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

import javax.management.ObjectName;

import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    /**
     * This {@link Restrictor} will be available in Spring's context and used later in
     * {@link org.jolokia.server.core.http.AgentServlet}.
     * @return
     */
    @Bean
    public Restrictor customRestrictor() {
        return new Restrictor() {
            @Override
            public boolean isHttpMethodAllowed(HttpMethod pMethod) {
                return true;
            }

            @Override
            public boolean isTypeAllowed(RequestType pType) {
                return true;
            }

            @Override
            public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
                return true;
            }

            @Override
            public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
                return true;
            }

            @Override
            public boolean isOperationAllowed(ObjectName pName, String pOperation) {
                return true;
            }

            @Override
            public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
                return true;
            }

            @Override
            public boolean isOriginAllowed(String pOrigin, boolean pOnlyWhenStrictCheckingIsEnabled) {
                return true;
            }

            @Override
            public boolean isObjectNameHidden(ObjectName name) {
                return false;
            }
        };
    }

}
