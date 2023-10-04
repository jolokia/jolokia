package org.jolokia.service.jmx.handler.list;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * Keys used for generating the list result
 *
 * @author roland
 * @since 13.09.11
 */
public enum DataKeys {

    DESCRIPTION("desc"),
    CLASSNAME("class"),
    ERROR("error"),
    NAME("name"),
    TYPES("types"),
    ARGS("args"),
    RETURN_TYPE("ret"),
    OPERATIONS("op"),
    ATTRIBUTES("attr"),
    NOTIFICATIONS("notif"),
    TYPE("type"),
    READ_WRITE("rw");

    private final String key;

    /**
     * Create the constant with the given key
     * @param pKey string representation of the key
     */
    DataKeys(String pKey) {
        key = pKey;
    }

    /**
     * Get the key of the enum
     * @return key as string
     */
    public String getKey() {
        return key;
    }


    @Override
    public String toString() {
        return getKey();
    }
}
