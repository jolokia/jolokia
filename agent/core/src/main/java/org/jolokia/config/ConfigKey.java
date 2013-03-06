package org.jolokia.config;

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
 * Enumeration defining the various configuration constant names which
 * can be used to configure the agent globally (e.g. in web.xml) or
 * as proccessing parameters (e.g. as query params).
 *
 * @author roland
 * @since Jan 1, 2010
 */
public enum ConfigKey {

    /**
     * Maximum number of history entries to keep
     */
    HISTORY_MAX_ENTRIES("historyMaxEntries",true, false, "10"),

    /**
     * Whether debug is switched on or not
     */
    DEBUG("debug",true, false, "false"),

    /**
     * Maximum number of debug entries to hold
     */
    DEBUG_MAX_ENTRIES("debugMaxEntries",true, false, "100"),

    /**
     * Request Dispatcher to use in addition to the local dispatcher.
     */
    DISPATCHER_CLASSES("dispatcherClasses", true, false),

    /**
     * Maximum traversal depth for serialization of complex objects.
     */
    MAX_DEPTH("maxDepth",true, true, null),

    /**
     * Maximum size of collections returned during serialization.
     * If larger, the collection is truncated
     */
    MAX_COLLECTION_SIZE("maxCollectionSize",true, true, null),

    /**
     * Maximum number of objects returned by serialization
     */
    MAX_OBJECTS("maxObjects",true, true, null),

    /**
     * Init parameter for the location of the policy file
     */
    POLICY_LOCATION("policyLocation",true,false,"classpath:/jolokia-access.xml"),

    /**
     * Runtime configuration (i.e. must come in with a request)
     * for ignoring errors during JMX operations and JSON serialization.
     * This works only for certain operations like pattern reads.
     */
    IGNORE_ERRORS("ignoreErrors", false, true),

    /**
     * Whether to include a stack trace in the response when an error occurs.
     * The allowed values are "true" for inclusion, "false" if no stacktrace
     * should be included or "runtime" if only {@link RuntimeException}s should
     * be included. Default is "true"
     */
    INCLUDE_STACKTRACE("includeStackTrace", true, true, "true"),

    /**
     * Whether to include a JSON serialized version of the exception. If set
     * to "true", the exception is added under the key "error_value" in
     * the response. Default is false.
     */
    SERIALIZE_EXCEPTION("serializeException", true, true, "false"),
    /**
     * Whether  property keys of ObjectNames should be ordered in the canonical way or in the way that they
     * are created.
     * The allowed values are either "true" in which case the canonical key order (== alphabetical
     * sorted) is used or "false" for getting the keys as registered. Default is "true"
     */
    CANONICAL_NAMING("canonicalNaming", true, true, "true"),

    /**
     * Optional domain name for registering own MBeans
     */
    MBEAN_QUALIFIER("mbeanQualifier", true, false),

    /**
     * Option which can be given to a request to speficy a JSONP callback.
     * The generated answer will be of type text/javascript and it will
     * contain a Javascript function to be called.
     */
    CALLBACK("callback", false, true),

    /**
     * Mime Type to use for the response value. By default, this is
     * <code>text/plain</code>, but it could be useful to return
     * <code>application/json</code>, too. A request parameter overrides a global
     * configuration.
     */
    MIME_TYPE("mimeType", true, true, "text/plain"),

    /**
     * For LIST requests, this option can be used to return
     * the result only if they set of registered MBeans has
     * been changed since the timestamp given in this option.
     * The timestamp has to be given in seconds since 1.1.1970
     * (epoch time).
     */
    IF_MODIFIED_SINCE("ifModifiedSince",false,true),

    // ================================================================================
    // Configuration relevant for OSGI container

    /**
     *  User for authentication purposes. Used by OSGi and JDK agent.
     */
    USER("user", false, true),

    /**
     *  Password for authentication purposes. Used by OSGi and JDK agent
     */
    PASSWORD("password", false, true),

    /**
     * Context used for agent, used e.g. in the OSGi activator
     * (but not for the servlet, this is done in web.xml)
     */
    AGENT_CONTEXT("agentContext", true, false, "/jolokia"),

    /**
     * For OSGi, if set to true, the agent uses a restrictor service when it kicks in,
     * but denies access otherwise.
     */
    USE_RESTRICTOR_SERVICE("useRestrictorService", true, false, "false"),

    /**
     * By default, the OSGi Agent listens for an OSGi HttpService to which it will register
     * an agent servlet. Set this to false if you want to instantiate the
     * servlet on your own (either declaratively within another war or programmatically)
     */
    LISTEN_FOR_HTTP_SERVICE("listenForHttpService", true, false, "true"),

    /**
     * By default, the OSGi Agent will bind to all HttpService implementations.
     * Set this to control which of the implementations of HttpService are bound to.
     * <p>The syntax is that of the standard OSGi Filter.</p>
     * <pre><code>
     *     (VirtualServer=__asadmin)  - Glassfish 3+ administration server
     * </code></pre>
     * <p>Note this will be combined with the objectClass filter for HttpService with
     * the and (&amp;) operator.</p>
     */
    HTTP_SERVICE_FILTER("httpServiceFilter",true,false,""),

    /**
     * Extra options passed to a server handle after it has been detected. The value
     * must be a JSON object with the product name as key and another JSON object as value containing
     * the specific handle configuration.
     *
     * E.g.
     *
     * <pre>
     *     {
     *         "glassfish" : { "bootAmx" : true},
     *         "jboss" : { "disableWorkaround" : true}
     *     }
     * </pre>
     */
    DETECTOR_OPTIONS("detectorOptions",true, false);

    private String key;
    private String defaultValue;
    private boolean globalConfig;
    private boolean requestConfig;

    private static Map<String, ConfigKey> keyByName;
    private static Map<String, ConfigKey> globalKeyByName;
    private static Map<String, ConfigKey> requestKeyByName;

    // Build up internal reverse map
    static {
        keyByName = new HashMap<String, ConfigKey>();
        globalKeyByName = new HashMap<String, ConfigKey>();
        requestKeyByName = new HashMap<String, ConfigKey>();
        for (ConfigKey ck : ConfigKey.values()) {
            keyByName.put(ck.getKeyValue(),ck);
            if (ck.isGlobalConfig()) {
                globalKeyByName.put(ck.getKeyValue(),ck);
            }
            if (ck.isRequestConfig()) {
                requestKeyByName.put(ck.getKeyValue(),ck);
            }
        }
    }


    ConfigKey(String pValue,boolean pIsGlobalConfig,boolean pIsRequestConfig) {
        this(pValue,pIsGlobalConfig,pIsRequestConfig,null);
    }

    ConfigKey(String pValue, boolean pIsGlobalConfig, boolean pIsRequestConfig, String pDefault) {
        key = pValue;
        defaultValue = pDefault;
        globalConfig = pIsGlobalConfig;
        requestConfig = pIsRequestConfig;
    }

    @Override
    /** {@inheritDoc} */
    public String toString() {
        return key;
    }

    /**
     * Get the configuration key for a global configuration
     *
     * @param pKeyS the key to lookup
     * @return the key or null if the key is not known or is not a global key
     */
    public static ConfigKey getGlobalConfigKey(String pKeyS) {
        return globalKeyByName.get(pKeyS);
    }

    /**
     * Get the configuration key for a request configuration
     *
     * @param pKeyS the key to lookup
     * @return the key or null if the key is not known or is not a request key
     */
    public static ConfigKey getRequestConfigKey(String pKeyS) {
        return requestKeyByName.get(pKeyS);
    }

    /**
     * Get the string value of a key
     *
     * @return string value of a key
     */
    public String getKeyValue() {
        return key;
    }

    /**
     * Get the default value
     *
     * @return the default value of a key
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Whether this key is a global configuration key
     * @return true if this is a global configuration key
     */
    public boolean isGlobalConfig() {
        return globalConfig;
    }


    /**
     * Whether this key is a request configuration key
     * @return true if this is a request configuration key
     */
    public boolean isRequestConfig() {
        return requestConfig;
    }
}
