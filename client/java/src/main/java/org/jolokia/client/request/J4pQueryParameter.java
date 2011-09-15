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
     * Option which can be given to a request to speficy a JSONP callback.
     * The generated answer will be of type text/javascript and it will
     * contain a JSON function to be called.
     */
    CALLBACK("callback")
    ;

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
