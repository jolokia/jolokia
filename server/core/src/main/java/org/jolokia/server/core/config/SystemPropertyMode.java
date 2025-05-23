/*
 * Copyright 2009-2013 Roland Huss
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

/**
 * Enumeration for how to handle system properties.
 *
 * @author roland
 * @since 01.01.13
 */
public enum SystemPropertyMode {

    /** Never check system properties. */
    NEVER("never"),

    /**
     * Check system properties if not resolvable in the specified properties.
     * This is the default. System properties prefixed with {@code jolokia.} and env variables
     * prefixed with {@code JOLOKIA_} are used when not specifed using other means (like JVM Agent
     * invocation options).
     */
    FALLBACK("fallback"),

    /**
     * Check system properties first, before trying the specified properties.
     * This allows system properties to override any other property source.
     */
    OVERRIDE("override");

    // Mode as it can be provided in the configuration
    private final String mode;

    SystemPropertyMode(String pMode) {
        mode = pMode;
    }

    /**
     * Get the enum for a given mode string, which is treated case insensitively.
     *
     * @param pMode mode to lookup
     * @return the mode or null if none could be found
     */
    public static SystemPropertyMode fromMode(String pMode) {
        if (pMode != null) {
            for (SystemPropertyMode m : SystemPropertyMode.values()) {
                if (m.mode.equals(pMode.toLowerCase())) {
                    return m;
                }
            }
        }
        return null;
    }

}
