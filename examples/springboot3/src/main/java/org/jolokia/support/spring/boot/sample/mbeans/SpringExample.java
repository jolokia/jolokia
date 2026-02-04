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
package org.jolokia.support.spring.boot.sample.mbeans;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(
    objectName = "jolokia.example:type=SpringManagedResource",
    description = "A class without any interface exported by Spring as Model MBean"
)
public class SpringExample {

    @ManagedAttribute(description = "Some statistics")
    public Map<String, Object> getStatistics() {
        return Map.of(
            "temperature", new BigDecimal("37.3"),
            "type", "Spring"
        );
    }

}
