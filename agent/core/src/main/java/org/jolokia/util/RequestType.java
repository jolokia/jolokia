package org.jolokia.util;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration for encapsulating the request mode.
 */
public enum RequestType {
    // Supported:
    READ("read"),
    LIST("list"),
    WRITE("write"),
    EXEC("exec"),
    VERSION("version"),
    SEARCH("search"),

    // Unsupported:
    REGNOTIF("regnotif"),
    REMNOTIF("remnotif");

    private String name;

    private static Map<String,RequestType> typesByNameMap = new HashMap<String, RequestType>();

    static {
        for (RequestType t : RequestType.values()) {
            typesByNameMap.put(t.getName(),t);
        }
    }

    RequestType(String pName) {
        name = pName;
    }

    public String getName() {
        return name;
    }

    /**
     * Get the request type by a string representation. This is case insensitive.
     * @param pName the type associated with the given name
     * @return type type looked up
     * @throws IllegalArgumentException if the argument is either <code>null</code> or
     *         does not map to a type.
     */
    public static RequestType getTypeByName(String pName) {
        if (pName == null) {
            throw new IllegalArgumentException("No type given");
        }
        RequestType type = typesByNameMap.get(pName.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("No type with name '" + pName + "' exists");
        }
        return type;
    }
}
