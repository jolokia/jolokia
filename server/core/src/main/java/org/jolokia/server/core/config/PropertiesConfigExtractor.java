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
import java.util.Properties;

public class PropertiesConfigExtractor implements ConfigExtractor {

    private final Properties properties;

    public PropertiesConfigExtractor(Properties properties) {
        this.properties = properties;
    }

    @Override
    public Enumeration<String> getNames() {
        return Collections.enumeration(properties.stringPropertyNames());
    }

    @Override
    public String getParameter(String key) {
        return properties.getProperty(key);
    }

}
