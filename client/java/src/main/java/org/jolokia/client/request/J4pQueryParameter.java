package org.jolokia.client.request;

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
 * Query parameters which can be used when requesting the server
 *
 * @author roland
 * @since 15.09.11
 */
// This class duplicates some parts from ConfigKey, however as this is a client lib
// we dont have a direct reference the module containing this class. Extracting ConfigKey
// in an extra module is currently overkill, though.
public enum J4pQueryParameter {

    /**
     * Maximum traversal depth for serialization of complex objects.
     */
    MAX_DEPTH("maxDepth"),

    /**
     * Maximum size of collections returned during serialization.
     * If larger, the collection is truncated
     */
    MAX_COLLECTION_SIZE("maxCollectionSize"),

    /**
     * Maximum number of objects returned by serialization
     */
    MAX_OBJECTS("maxObjects"),

    /**
     * Runtime configuration (i.e. must come in with a request)
     * for ignoring errors during JMX operations and JSON serialization.
     * This works only for certain operations like pattern reads.
     */
    IGNORE_ERRORS("ignoreErrors"),

    /**
     * Whether to include a stack trace in the response when an error occurs.
     * The allowed values are "true" for inclusion, "false" if no stacktrace
     * should be included or "runtime" if only {@link RuntimeException}s should
     * be included. Default is "true"
     */
    INCLUDE_STACKTRACE("includeStackTrace"),

    /**
     * Whether to include a JSON serialized version of the exception. If set
     * to "true", the exception is added under the key "error_value" in
     * the response. Default is false.
     */
    SERIALIZE_EXCEPTION("serializeException"),

    /**
     * Whether  property keys of ObjectNames should be ordered in the canonical way or in the way that they
     * are created.
     * The allowed values are either "true" in which case the canonical key order (== alphabetical
     * sorted) is used or "false" for getting the keys as registered. Default is "true"
     */
    CANONICAL_NAMING("canonicalNaming"),

    /**
     * For LIST request this property can be used to obtain the result (which can be
     * quite lengthy) only if the set of registered MBeans has been changed since
     * the last time (given as epoch time in seconds since 1.1.1970) provided with
     * this parameterd
     */
    IF_MODIFIED_SINCE("ifModifiedSince");
    // =======================================================================

    // Query parameter
    private String param;

    J4pQueryParameter(String pParam) {
        param = pParam;
    }

    public String getParam() {
        return param;
    }
}
