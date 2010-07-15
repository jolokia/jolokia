package org.jolokia;

import java.util.HashMap;
import java.util.Map;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
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
