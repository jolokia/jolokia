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

import org.jolokia.support.jmx.JsonMBean;

@JsonMBean
public class Jolokia implements JolokiaMBean {

    private Map<String, Object> statistics = Map.of(
        "temperature", new BigDecimal("36.6"),
        "kind", "JsonMBean"
    );

    @Override
    public Map<String, Object> getStatistics() {
        return statistics;
    }

    @Override
    public void setStatistics(Map<String, Object> statistics) {
        this.statistics = statistics;
    }

}
