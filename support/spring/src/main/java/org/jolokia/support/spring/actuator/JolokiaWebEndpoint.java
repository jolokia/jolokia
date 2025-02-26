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

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.web.servlet.ModelAndView;

/**
 * Jolokia actuator endpoint ({@code /actuator/jolokia}) which used to register a servlet, but now only
 * registers a link under {@code /actuator} listing page.
 */
@WebEndpoint(id = "jolokia")
public class JolokiaWebEndpoint {

    /**
     * This operation is not invoked, because actual {@link JolokiaServletRegistration Jolokia servlet registration}
     * is overriding {@code /actuator/jolokia/*} mapping - taking into account actuator base/context path
     * configuration
     * @return
     */
    @ReadOperation
    public ModelAndView jolokia() {
        return new ModelAndView("redirect:/actuator/jolokia/version");
    }

}
