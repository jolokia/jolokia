package org.jolokia;

import java.util.HashMap;
import java.util.Map;

/*
 *  Copyright 2009-2010 Roland Huss
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
 * @author roland
 * @since Jan 1, 2010
 */
public enum ConfigKey {

    // Maximum number of history entries to keep
    HISTORY_MAX_ENTRIES("historyMaxEntries","10"),

    // Whether debug is switched on or not
    DEBUG("debug","false"),

    // Maximum number of debug entries to hold
    DEBUG_MAX_ENTRIES("debugMaxEntries","100"),

    // Dispatcher to use
    DISPATCHER_CLASSES("dispatcherClasses"),

    // Maximum traversal depth for serialization of complex objects.
    MAX_DEPTH("maxDepth",null),

    // Maximum size of collections returned during serialization.
    // If larger, the collection is truncated
    MAX_COLLECTION_SIZE("maxCollectionSize",null),

    // Maximum number of objects returned by serialization
    MAX_OBJECTS("maxObjects",null),

    // Context used for agent, used e.g. in the OSGi activator
    // (but not for the servlet, this is done in web.xml)
    AGENT_CONTEXT("agentContext","/jolokia"),

    // User and password for authentication purposes.
    USER("user"),
    PASSWORD("password"),

    // Runtime configuration (i.e. must come in with a request)
    // for ignoring errors during JMX operations and JSON serialization.
    // This works only for certain operations like pattern reads.
    IGNORE_ERRORS("ignoreErrors"),

    // Optional domain name for registering own MBeans
    MBEAN_QUALIFIER("mbeanQualifier");

    private String key;
    private String defaultValue;
    private static Map<String, ConfigKey> keyByName;

    // Build up internal reverse map
    static {
        keyByName = new HashMap<String, ConfigKey>();
        for (ConfigKey ck : ConfigKey.values()) {
            keyByName.put(ck.getKeyValue(),ck);
        }
    }

    ConfigKey(String pValue) {
        this(pValue,null);
    }

    ConfigKey(String pValue, String pDefault) {
        key = pValue;
        defaultValue = pDefault;
    }

    @Override
    public String toString() {
        return key;
    }

    public static ConfigKey getByKey(String pKeyS) {
        return keyByName.get(pKeyS);
    }

    public String getKeyValue() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
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
