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
package org.jolokia.client;

/**
 * <p>Query parameters which can be used when sending a {@link org.jolokia.client.request.JolokiaRequest} to
 * remote Jolokia Agent.</p>
 *
 * <p>The query parameters are the same options that the server uses in {@code org.jolokia.server.core.config.ConfigKey}
 * enum, however Jolokia client libraries do not depend on the server libraries, so a bit of duplication
 * can't be avoided. All of the config keys which use {@code requestConfig=true} are duplicated here (except
 * "callback" which is purely for JavaScript and JSON-P).</p>
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
     * If larger, the collection is truncated.
     */
    MAX_COLLECTION_SIZE("maxCollectionSize"),

    /**
     * Maximum number of objects returned by serialization
     */
    MAX_OBJECTS("maxObjects"),

    /**
     * How to serialize long values: "number" (as JSON number) or "string" (as JSON string). This is an option
     * mostly for JavaScript clients, because JavaScript numbers and Java longs use different bit length.
     * See <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER">Number.MAX_SAFE_INTEGER</a>
     */
    SERIALIZE_LONG("serializeLong"),

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
     * Mime type to use. Should be {@code text/plain} or (default) {@code application/json}.
     */
    MIME_TYPE("mimeType"),

    /**
     * For security reasons (writing responses directly into some DB), it may be required to exclude
     * the request from its response JSON. This parameter can control the inclusion of request.
     */
    INCLUDE_REQUEST("includeRequest"),

    /**
     * A request parameter for {@code list} operation, which tells Jolokia to return a map of the keys obtained from
     * {@link javax.management.ObjectName#getKeyPropertyList()} under {@code keys} field of the data for an MBean.
     */
    LIST_KEYS("listKeys"),

    /**
     * For LIST request this property can be used to obtain the result (which can be
     * quite lengthy) only if the set of registered MBeans has been changed since
     * the last time (given as epoch time in seconds since 1.1.1970) provided with
     * this parameter.
     */
    IF_MODIFIED_SINCE("ifModifiedSince"),

    /**
     * Query parameter used for providing a path in order to avoid escaping
     * issues. This can be used as an alternative for path notations with {@link org.jolokia.client.request.HttpMethod#GET}
     * requests. For {@link org.jolokia.client.request.HttpMethod#POST} requests, the path is sent within the
     * JSON body.
     */
    PATH_QUERY_PARAM("p"),

    /**
     * Processing parameter used to enable <em>smart list response</em> where JSON data for each {@link javax.management.MBeanInfo}
     * is cached instead of being duplicated for each (potentially the same) MBean of similar class.
     */
    LIST_CACHE("listCache");

    private final String param;

    J4pQueryParameter(String pParam) {
        param = pParam;
    }

    public String getParam() {
        return param;
    }

}
