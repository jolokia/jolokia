/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.support.spring.boot.sample;

import org.jolokia.support.spring.boot.sample.mbeans.ExampleMXBean;
import org.jolokia.support.spring.boot.sample.mbeans.JolokiaMBean;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class Hints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern("META-INF/web-resources/**");
        hints.resources().registerPattern("log4j2.properties");

        hints.proxies().registerJdkProxy(ExampleMXBean.class);
        hints.proxies().registerJdkProxy(JolokiaMBean.class);
    }

}
