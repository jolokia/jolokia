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

package org.jolokia.server.core.config;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jolokia.server.core.util.InetAddresses;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.server.core.util.StringUtil;

/**
 * <p>Class encapsulating Agent's global configuration (processing parameters are accessed using
 * {@link org.jolokia.server.core.request.ProcessingParameters}.</p>
 *
 * <p>There are various <em>sources</em> of configuration values and here's the order (later ones override earlier
 * sources):<ol>
 *     <li>{@link ConfigKey#getDefaultValue()}</li>
 *     <li>Properties from {@code /default-jolokia-agent.properties} (JVM Agent only)</li>
 *     <li>Properties from file specified for {@code config} option (JVM Agent only)</li>
 *     <li>Remaining options from JVM Agent invocation ({@code premain()} / {@code agentmain()} method) (JVM Agent only)</li>
 *     <li>Servlet config parameters (Servlet Agent only)</li>
 *     <li>Servlet context parameters (Servlet Agent only)</li>
 *     <li>System properties prefixed with {@code jolokia.}</li>
 *     <li>Environment variables prefixed with {@code JOLOKIA_}</li>
 * </ol></p>
 *
 * <p>Specific agent has to call {@link #update} method in proper order. No need to <em>update</em> this configuration
 * with system properties / environment variables, as these are kept in separate maps and checked in the first
 * place.</p>
 *
 * <p>In all scenarios, property values are immediately resolved when they are in one of these forms:<ul>
 *     <li>{@code ${env:PROPERTY_NAME}} - value is taken from environment even if the property isn't prefixed with
 *     {@code JOLOKIA_}.</li>
 *     <li>{@code ${sys:propertyName}}/{@code ${prop:propertyName}} - value is taken from system properties even if the property isn't prefixed
 *     with {@code jolokia.}.</li>
 *     <li>{@code ${host}} - value from {@link InetAddress#getHostName()}, where {@link InetAddress} is IPv4 address of local interface (best guess)</li>
 *     <li>{@code ${host6}} - value from {@link InetAddress#getHostName()}, where {@link InetAddress} is IPv6 address of local interface (best guess)</li>
 *     <li>{@code ${host:<interface>}} - value from {@link InetAddress#getHostName()}, where {@link InetAddress} is IPv4 address of local interface (by interface name like {@code eth0})</li>
 *     <li>{@code ${host6:<interface>}} - value from {@link InetAddress#getHostName()}, where {@link InetAddress} is IPv6 address of local interface (by interface name like {@code eth0})</li>
 *     <li>{@code ${ip}} - value from {@link InetAddress#getHostAddress()}, where {@link InetAddress} is IPv4 address of local interface (best guess)</li>
 *     <li>{@code ${ip6}} - value from {@link InetAddress#getHostAddress()}, where {@link InetAddress} is IPv6 address of local interface (best guess)</li>
 *     <li>{@code ${ip:<interface>}} - value from {@link InetAddress#getHostAddress()}, where {@link InetAddress} is IPv4 address of local interface (by interface name like {@code eth0})</li>
 *     <li>{@code ${ip6:<interface>}} - value from {@link InetAddress#getHostAddress()}, where {@link InetAddress} is IPv6 address of local interface (by interface name like {@code eth0})</li>
 * </ul></p>
 *
 * <p>IP Address resolution uses some heuristics - physical interfaces are preferred over virtual ones and global
 * IP address are preferred over site/link local ones (like 169.254.x.x or fe80::).</p>
 *
 * @author roland
 * @since 07.02.13
 */
public class StaticConfiguration implements Configuration {

    // Jolokia properties found directly in System.getenv() - only ones prefixed with "JOLOKIA_"
    private Map<ConfigKey, String> environmentVariables;
    // Jolokia properties found directly in System.getProperties() - only ones prefixed with "jolokia."
    private Map<ConfigKey, String> systemProperties;
    // system properties to be used by StringUtil for resolution - include host/ip properties
    private Properties properties;

    private SystemPropertyMode systemPropertyMode = SystemPropertyMode.OVERRIDE;

    // The global configuration provided in constructor - override'able with update()
    private final Map<ConfigKey, String> configMap = new HashMap<>();

    private final Set<ConfigKey> keys = new HashSet<>();

    private InetAddresses defaultAddresses;
    private final Map<String, String> networkConfig = new HashMap<>();

    /**
     * Convenience constructor for setting up base configuration with key values pairs. This constructor
     * is especially suited for unit tests. The placeholder values are resolved.
     *
     * @param keyAndValues an array with even number of elements and ConfigKey and String alternating.
     */
    public StaticConfiguration(Object... keyAndValues) {
        initialize();
        int idx = 0;
        for (int i = idx; i < keyAndValues.length; i += 2) {
            configMap.put((ConfigKey) keyAndValues[i], resolve((String) keyAndValues[i + 1]));
            keys.add((ConfigKey) keyAndValues[i]);
        }
    }

    /**
     * Initialise this configuration from a string-string map. Only the known keys are taken
     * from the given map and the placeholder values are resolved
     *
     * @param pConfig config map from where to take the configuration
     */
    public StaticConfiguration(Map<String, String> pConfig) {
        initialize();
        for (ConfigKey c : ConfigKey.values()) {
            String value = pConfig.get(c.getKeyValue());
            if (value != null) {
                configMap.put(c, resolve(value));
                keys.add(c);
            }
        }
    }

    public void setSystemPropertyMode(SystemPropertyMode systemPropertyMode) {
        this.systemPropertyMode = systemPropertyMode;
    }

    /**
     * Check if there are any Jolokia properties specified using environment variables and/or system properties.
     * Values are <em>not</em> placeholder-resolved.
     */
    private void initialize() {
        this.environmentVariables = new HashMap<>();
        Map<String, String> env = new HashMap<>(env());
        this.systemProperties = new HashMap<>();
        Map<String, String> sys = new HashMap<>();
        this.properties = new Properties();
        this.properties.putAll(System.getProperties());
        Properties p = sys();
        for (String key : p.stringPropertyNames()) {
            sys.put(key, p.getProperty(key));
        }

        for (ConfigKey c : ConfigKey.values()) {
            String envKey = c.asEnvVariable();
            if (env.containsKey(envKey)) {
                this.environmentVariables.put(c, env.get(envKey));
                keys.add(c);
            }
            String sysKey = c.asSystemProperty();
            if (sys.containsKey(sysKey)) {
                this.systemProperties.put(c, sys.get(sysKey));
                keys.add(c);
            }
        }

        // check network config and populate the map with keys: ip, ip6, host, host6 and versions with :<interface-id>
        NetworkInterface nif = NetworkUtil.getBestMatchNetworkInterface();
        Map<String, InetAddresses> config = NetworkUtil.getBestMatchAddresses();

        config.forEach((name, addresses) -> {
            String ip6Address = addresses.getIa6().getHostAddress();
            if (addresses.getIa6().getScopedInterface() != null || addresses.getIa6().getScopeId() > 0) {
                int percent = ip6Address.indexOf('%');
                if (percent != -1) {
                    ip6Address = ip6Address.substring(0, percent);
                }
            }

            if (nif != null && name.equals(nif.getName())) {
                networkConfig.put("ip", addresses.getIa4().getHostAddress());
                networkConfig.put("ip6", ip6Address);
                networkConfig.put("host", addresses.getIa4().getHostName());
                networkConfig.put("host6", addresses.getIa6().getHostName());
            }
            networkConfig.put("ip:" + name, addresses.getIa4().getHostAddress());
            networkConfig.put("ip6:" + name, ip6Address);
            networkConfig.put("host:" + name, addresses.getIa4().getHostName());
            networkConfig.put("host6:" + name, addresses.getIa6().getHostName());
        });
        this.properties.putAll(networkConfig);
    }

    /**
     * Resolves any placeholder in the form of supported {@code ${xxx}} syntax - using env variables, sys properties
     * or host/host6/ip/ip6 values.
     * @param value
     * @return
     */
    public String resolve(String value) {
        return StringUtil.resolvePlaceholders(value, sys(), env());
    }

    protected Map<String, String> env() {
        return System.getenv();
    }

    protected Properties sys() {
        return this.properties;
    }

    /**
     * Update the configuration hold by this object overriding existing values (but not the sys/env variables
     * used during initialization). The placeholder values are resolved.
     *
     * @param pExtractor an extractor for retrieving the configuration from some external object
     */
    public void update(ConfigExtractor pExtractor) {
        Enumeration<String> e = pExtractor.getNames();
        while (e.hasMoreElements()) {
            String keyS = e.nextElement();
            ConfigKey key = ConfigKey.getGlobalConfigKey(keyS);
            if (key != null) {
                configMap.put(key, resolve(pExtractor.getParameter(keyS)));
                keys.add(key);
            }
        }
    }

    /**
     * Update from another configuration object whose values take precedence. The placeholder values are resolved.
     *
     * @param pConfig update configuration from the given config
     */
    public void update(Configuration pConfig) {
        for (ConfigKey key : pConfig.getConfigKeys()) {
            configMap.put(key, resolve(pConfig.getConfig(key)));
        }
    }

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pKey) {
        return getConfig(pKey, true);
    }

    private String getConfig(ConfigKey pKey, boolean checkSysOrEnv) {
        String v;
        if (systemPropertyMode == SystemPropertyMode.OVERRIDE) {
            v = environmentVariables.get(pKey);
            if (v != null) {
                return v;
            }
            v = systemProperties.get(pKey);
            if (v != null) {
                return v;
            }
        }
        v = configMap.get(pKey);
        if (v != null) {
            return v;
        }
        if (systemPropertyMode == SystemPropertyMode.FALLBACK) {
            v = environmentVariables.get(pKey);
            if (v != null) {
                return v;
            }
            v = systemProperties.get(pKey);
            if (v != null) {
                return v;
            }
        }
        return pKey.getDefaultValue();
    }

    /** {@inheritDoc} */
    public Set<ConfigKey> getConfigKeys() {
        return keys;
    }

    /** {@inheritDoc} */
    public boolean containsKey(ConfigKey pKey) {
        return keys.contains(pKey);
    }
}
