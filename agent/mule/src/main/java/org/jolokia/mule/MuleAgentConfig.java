package org.jolokia.mule;

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
 * Agent Configuration. For documentation of the configuration
 * parameters please refer to {@link org.jolokia.config.ConfigKey}
 *
 * @author roland
 * @since 30.08.11
 */
public interface MuleAgentConfig {
    /**
     * Hostname to bind the server to or <code>null</code> if
     * the default jetty host is to be used
     *
     * @return hostname
     */
    String getHost();

    /**
     * Port to bind to
     * @return the server port
     */
    int getPort();

    /**
     * User when authentication is active
     * @return user name
     */
    String getUser();

    /**
     * Password when authentication is used
     * @return password
     */
    String getPassword();

    /**
     * How many debug entries to keep within the debug history
     * @return max debug entries to keep
     */
    int getDebugMaxEntries();

    /**
     * Maximum number of entries to keep in the call history
     * @return maxium histories entries
     */
    int getHistoryMaxEntries();

    /**
     * Maximum collection size after which a collection is trunctated for
     * a return value
     *
     * @return maximum collection size
     */
    int getMaxCollectionSize();

    /**
     * How many level deep to go when serializing beans for a return value
     * @return max level
     */
    int getMaxDepth();

    /**
     * Maximum number of object to serialize for a response
     * @return maximum number of objects
     */
    int getMaxObjects();

    /**
     * Whether debug is switched on
     * @return true if debug is switched on
     */
    boolean isDebug();


}
