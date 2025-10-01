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
package org.jolokia.client;

/**
 * Enumeration of all operations supported by Jolokia protocol. Each operation has associated request and response
 * messages - some trivial (empty), some complex. Some are strict, some are flexible.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public enum JolokiaOperation {

    // Supported

    /** Read operations is for getting MBean attributes */
    READ("read"),

    /** Write operations is for setting MBean attributes and retrieving previous values (if not write-only) */
    WRITE("write"),

    /** Execution operation is for invoking MBean operations with passed parameters and retrieving return values */
    EXEC("exec"),

    /** Listing available MBeans including their {@link javax.management.MBeanInfo} */
    LIST("list"),

    /** Searching for MBean names using MBean {@link javax.management.ObjectName} patterns */
    SEARCH("search"),

    /** Version operation retrieves information about Jolokia agent */
    VERSION("version"),

    /**
     * Configuration operation exposes <em>metadata</em> about running Jolokia Agent. It's different than
     * {@link #VERSION}, as it should be explicitly available without any authentication/authorization. The reason
     * is that this operation provides information about available authentication mechanisms that protect all other
     * operations.
     *
     * @since 2.4.0
     */
    CONFIG("config"),

    // Unsupported (planned, imagined)

    REGNOTIF("regnotif"),
    REMNOTIF("remnotif");

    private final String value;

    JolokiaOperation(String pValue) {
        value = pValue;
    }

    /**
     * Get operation type as lower-case String value.
     * @return
     */
    public String getValue() {
        return value;
    }

}
