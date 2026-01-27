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
package org.jolokia.core.util;

import java.io.InputStream;
import java.util.Properties;

public class PropertyUtil {

    public static String VERSION;

    private PropertyUtil() {}

    static {
        try (InputStream is = PropertyUtil.class.getResourceAsStream("/jolokia-core-version.properties")) {
            Properties props = new Properties();
            props.load(is);
            VERSION = props.getProperty("version");
        } catch (Exception e) {
            VERSION = "<unknown>";
        }
    }

    /**
     * Used to determine a system property name for Jolokia option. For example changes {@code debugMaxEntries}
     * into {@code jolokia.debugMaxEntries}
     *
     * @param property
     * @return
     */
    public static String asJolokiaSystemProperty(String property) {
        if (property == null) {
            return null;
        }
        return "jolokia." + property;
    }

    /**
     * Used to determine an environment property name for Jolokia option. For example changes {@code debugMaxEntries}
     * into {@code JOLOKIA_DEBUG_MAX_ENTRIES}
     *
     * @param property
     * @return
     */
    public static String asJolokiaEnvVariable(String property) {
        if (property == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        boolean notFirst = false;
        for (char c : property.toCharArray()) {
            if (Character.isUpperCase(c) && notFirst) {
                buf.append("_").append(c);
            } else {
                buf.append(Character.toUpperCase(c));
            }
            notFirst = true;
        }
        buf.insert(0, "JOLOKIA_");

        return buf.toString();
    }

    /**
     * Converts jolokia option name specified as system property (in {@code jolokia.camelCase} format).
     *
     * @param property
     * @return
     */
    public static String fromJolokiaSystemProperty(String property) {
        if (property == null) {
            return null;
        }
        if (!property.startsWith("jolokia.")) {
            return null;
        }

        return property.substring("jolokia.".length());
    }

    /**
     * Converts jolokia option name specified as environment variable (in {@code JOLOKIA_SCREAMING_SNAKE_CASE} format).
     *
     * @param property
     * @return
     */
    public static String fromJolokiaEnvVariable(String property) {
        if (property == null) {
            return null;
        }
        if (!property.startsWith("JOLOKIA_")) {
            return null;
        }
        property = property.substring("JOLOKIA_".length());
        String[] parts = property.split("_");
        StringBuilder buf = new StringBuilder();
        buf.append(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                buf.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1).toLowerCase());
            }
        }

        return buf.toString();
    }

}
