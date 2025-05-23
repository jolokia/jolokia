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
package org.jolokia.server.core.config;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MapConfigExtractor implements ConfigExtractor {

    private final Map<String, String> properties;
    // predicate to check if the key is accepted
    private final Predicate<String> acceptsKey;

    public MapConfigExtractor(Map<String, String> properties) {
        this.properties = properties;
        this.acceptsKey = (key) -> true;
    }

    public MapConfigExtractor(Map<String, String> properties, Predicate<String> acceptsKey) {
        this.properties = properties;
        this.acceptsKey = acceptsKey;
    }

    @Override
    public Enumeration<String> getNames() {
        return Collections.enumeration(properties.keySet().stream()
            .filter(acceptsKey).collect(Collectors.toList()));
    }

    @Override
    public String getParameter(String key) {
        return acceptsKey.test(key) ? properties.get(key) : null;
    }

}
