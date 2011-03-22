/*
 * Copyright 2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.config;

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

    // Maximum number of history entries to keep
    HISTORY_MAX_ENTRIES("historyMaxEntries",true, false, "10"),

    // Whether debug is switched on or not
    DEBUG("debug",true, false, "false"),

    // Maximum number of debug entries to hold
    DEBUG_MAX_ENTRIES("debugMaxEntries",true, false, "100"),

    // Dispatcher to use
    DISPATCHER_CLASSES("dispatcherClasses", true, false),

    // Maximum traversal depth for serialization of complex objects.
    MAX_DEPTH("maxDepth",true, true, null),

    // Maximum size of collections returned during serialization.
    // If larger, the collection is truncated
    MAX_COLLECTION_SIZE("maxCollectionSize",true, true, null),

    // Maximum number of objects returned by serialization
    MAX_OBJECTS("maxObjects",true, true, null),

    // Context used for agent, used e.g. in the OSGi activator
    // (but not for the servlet, this is done in web.xml)
    AGENT_CONTEXT("agentContext",true, false, "/jolokia"),

    // Init parameter for the location of the policy file
    POLICY_LOCATION("policyLocation",true,false,"classpath:/jolokia-access.xml"),

    // User and password for authentication purposes.
    USER("user", false, true),
    PASSWORD("password", false, true),

    // Runtime configuration (i.e. must come in with a request)
    // for ignoring errors during JMX operations and JSON serialization.
    // This works only for certain operations like pattern reads.
    IGNORE_ERRORS("ignoreErrors", false, true),

    // Optional domain name for registering own MBeans
    MBEAN_QUALIFIER("mbeanQualifier", true, false),

    // Option which can be given to a request to speficy a JSONP callback.
    // The generated answer will be of type text/javascript and it will
    // contain a JSON function to be called.
    CALLBACK("callback", false, true);

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
    public String toString() {
        return key;
    }

    public static ConfigKey getConfigKey(String pKeyS) {
        return keyByName.get(pKeyS);
    }

    public static ConfigKey getGlobalConfigKey(String pKeyS) {
        return globalKeyByName.get(pKeyS);
    }
    public static ConfigKey getRequestConfigKey(String pKeyS) {
        return requestKeyByName.get(pKeyS);
    }

    public String getKeyValue() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isGlobalConfig() {
        return globalConfig;
    }

    public boolean isRequestConfig() {
        return requestConfig;
    }

    // Extract value from map, including a default value if
    // value is not set
    public String getValue(Map<ConfigKey, String> pConfig) {
        String value = pConfig.get(this);
        if (value == null) {
            value = this.getDefaultValue();
        }
        return value;
    }

    // Extract config options from a given map
    public static Map<ConfigKey,String> extractConfig(Map<String,String> pMap) {
        Map<ConfigKey,String> ret = new HashMap<ConfigKey, String>();
        for (ConfigKey c : ConfigKey.values()) {
            String value = pMap.get(c.getKeyValue());
            if (value != null) {
                ret.put(c,value);
            }
        }
        return ret;
    }
}
