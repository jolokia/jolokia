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
package org.jolokia.server.core.util;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Utilities for String manipulation. See Hawtio's {@code io.hawt.util.Strings}.
 */
public final class StringUtil {

    private static final Set<String> networkKeys = Set.of("ip", "ip6", "host", "host6");

    private StringUtil() {
    }

    public static String resolvePlaceholders(String value) {
        return resolvePlaceholders(value, System.getProperties(), System.getenv());
    }

    /**
     * Simple, recursively-safe property placeholder resolver. De-facto
     * standard {@code ${...}} syntax is used. Unresolvable properties are not replaced and separators pass to
     * resulting value.
     *
     * @param value
     * @param sys source of system properties to look up when resolving placeholders like {@code ${prop:xxx}}/{@code ${sys:xxx}}
     * @param env source of environment variables to look up when resolving placeholders like {@code ${env:xxx}}
     * @return
     */
    public static String resolvePlaceholders(String value, Properties sys, Map<String, String> env) {
        if (value == null || !value.contains("$")) {
            return value;
        }

        StringBuilder result = new StringBuilder();
        int l = value.length();
        for (int pos1 = 0; pos1 < l; pos1++) {
            char c1 = value.charAt(pos1);
            char c2 = pos1 == l - 1 ? '\0' : value.charAt(pos1 + 1);
            if (c1 == '$' && c2 == '{') {
                // find matching }
                //  - if found, resolve and continue with the rest of the value
                //  - if not found, just proceed (possibly until next "${")
                int depth = 1;
                int pos2 = pos1 + 2;
                while (depth > 0 && pos2 < l) {
                    if (value.charAt(pos2) == '$' && pos2 < l - 1 && value.charAt(pos2 + 1) == '{') {
                        depth++;
                        pos2 += 2;
                    } else if (value.charAt(pos2) == '}') {
                        depth--;
                        pos2++;
                    } else {
                        pos2++;
                    }
                }
                if (depth > 0) {
                    // no matching '}'
                    result.append('$');
                } else {
                    pos1 = resolve(value, result, pos1, pos2, sys, env) - 1;
                }
            } else {
                result.append(c1);
            }
        }

        return result.toString();
    }

    /**
     * Single iteration resolve method. {@code from} indicates <code>${</code> placeholder start
     *
     * @param value
     * @param result
     * @param from
     * @param to
     * @param sys
     * @param env
     * @return
     */
    private static int resolve(String value, StringBuilder result, int from, int to,
                               Properties sys, Map<String, String> env) {
        // "from" always points to "${" and "to" points to _matching_ "}"
        String key = resolvePlaceholders(value.substring(from + 2, to - 1), sys, env).trim();
        if (key.startsWith("env:")) {
            String v = env.get(key.substring(4));
            result.append(v);
            return to;
        }
        boolean sp = false;
        if (key.startsWith("sys:")) {
            key = key.substring(4);
            sp = true;
        }
        if (key.startsWith("prop:")) {
            key = key.substring(5);
            sp = true;
        }
        if (sp && sys.containsKey(key)) {
            String v = sys.getProperty(key);
            result.append(v);
            return to;
        }
        // could be networking property
        if (networkKeys.contains(key) && sys.containsKey(key)) {
            // take them from "sys" as well
            String v = sys.getProperty(key);
            result.append(v);
            return to;
        }
        int idx = key.indexOf(":");
        if (idx > 0) {
            String prefix = key.substring(0, idx);
            if (networkKeys.contains(prefix) && sys.containsKey(key)) {
                String v = sys.getProperty(key);
                result.append(v);
                return to;
            }
        }
        if (sys.containsKey(key)) {
            String v = sys.getProperty(key);
            result.append(v);
            return to;
        }

        result.append("${").append(key).append("}");
        return to;
    }

}
