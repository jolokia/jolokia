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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.jolokia.server.core.service.api.SecurityDetails;
import org.jolokia.server.core.util.InetAddresses;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.server.core.util.StringUtil;

/**
 * <p>Class encapsulating Agent's global configuration (processing parameters are accessed using
 * {@link org.jolokia.server.core.request.ProcessingParameters}).</p>
 *
 * <p>There are various <em>sources</em> of configuration values and here's the order (later ones override earlier
 * sources):<ol>
 *     <li>{@link ConfigKey#getDefaultValue()}</li>
 *     <li>Properties from {@code /default-jolokia-agent.properties} (JVM Agent only)</li>
 *     <li>Properties from file specified for {@code config} option (JVM Agent only)</li>
 *     <li>Environment variables prefixed with {@code JOLOKIA_}</li>
 *     <li>System properties prefixed with {@code jolokia.}</li>
 *     <li>Servlet config parameters (Servlet Agent only)</li>
 *     <li>Servlet context parameters (Servlet Agent only)</li>
 *     <li>Remaining options from JVM Agent invocation ({@code premain()} / {@code agentmain()} method) (JVM Agent only)</li>
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
    private final Properties properties;

    private final SystemPropertyMode systemPropertyMode;

    // The global configuration provided in constructor - overrideable with update()
    private final Map<ConfigKey, String> configMap = new HashMap<>();

    private final Set<ConfigKey> keys = new HashSet<>();

    private final Map<String, String> networkConfig = new HashMap<>();

    // Whether to allow reverse DNS lookup for resolving host names
    private boolean allowDnsReverseLookup;

    // Security details prepared after initialization of authentication mechanism (JVM Agent)
    // or by other means (WAR, SpringBoot, ...)
    private final SecurityDetails securityDetails = new SecurityDetails();

    /**
     * Convenience constructor for setting up base configuration with key values pairs. This constructor
     * is especially suited for unit tests. The placeholder values are resolved.
     *
     * @param keyAndValues an array with even number of elements and ConfigKey and String alternating.
     */
    public StaticConfiguration(Object... keyAndValues) {
        this.systemPropertyMode = SystemPropertyMode.FALLBACK;
        this.properties = new Properties();
        this.properties.putAll(System.getProperties());
        this.allowDnsReverseLookup = false;
        boolean allowDnsReverseLookupFound = false;
        for (int i = 0; i < keyAndValues.length; i += 2) {
            if (ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.equals(keyAndValues[i])) {
                this.allowDnsReverseLookup = Boolean.parseBoolean((String) keyAndValues[i + 1]);
                allowDnsReverseLookupFound = true;
                break;
            }
        }
        if (!allowDnsReverseLookupFound) {
            // try sys/env for this particular property
            String envKey = ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.asEnvVariable();
            if (env().containsKey(envKey)) {
                this.allowDnsReverseLookup = Boolean.parseBoolean(env().get(envKey));
            }
            String sysKey = ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.asSystemProperty();
            if (this.properties.containsKey(sysKey)) {
                this.allowDnsReverseLookup = Boolean.parseBoolean(this.properties.getProperty(sysKey));
            }
        }

        initializeFromNetwork();

        int idx = 0;
        for (int i = idx; i < keyAndValues.length; i += 2) {
            configMap.put((ConfigKey) keyAndValues[i], resolve((String) keyAndValues[i + 1]));
            keys.add((ConfigKey) keyAndValues[i]);
        }

        // now initialize from sys/env to override defaults
        initializeFromEnvironment(null);
    }

    /**
     * Initialise this configuration from a string-string map. Only the known keys are taken
     * from the given map and the placeholder values are resolved
     *
     * @param pConfig config map from where to take the configuration
     */
    public StaticConfiguration(Map<String, String> pConfig) {
        this(pConfig, null, SystemPropertyMode.FALLBACK);
    }

    /**
     * Initialise this configuration from a string-string map. Only the known keys are taken
     * from the given map and the placeholder values are resolved and stored within this {@link Configuration},
     * but if user passes a map in {@code pResolved} argument, it'll be populated with values from {@code pConfig}
     * but resolved - also the ones which do not have corresponding {@link ConfigKey}.
     *
     * @param pConfig config map from where to take the initial configuration - overrideable with sys/env
     *                properties and following {@link #update} methods
     * @param pResolved map to collect resolved properties
     * @param pSystemPropertyMode how to handle system properties
     */
    public StaticConfiguration(Map<String, String> pConfig, Map<String, String> pResolved, SystemPropertyMode pSystemPropertyMode) {
        this.systemPropertyMode = pSystemPropertyMode;
        this.properties = new Properties();
        this.properties.putAll(System.getProperties());
        if (pConfig.containsKey(ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.getKeyValue())) {
            this.allowDnsReverseLookup = Boolean.parseBoolean(pConfig.getOrDefault(ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.getKeyValue(), "false"));
        } else {
            // we need these before network initialization, which is then needed for resolution of other properties
            // try sys/env for this particular property
            String envKey = ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.asEnvVariable();
            if (env().containsKey(envKey)) {
                this.allowDnsReverseLookup = Boolean.parseBoolean(env().get(envKey));
            }
            String sysKey = ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.asSystemProperty();
            if (this.properties.containsKey(sysKey)) {
                this.allowDnsReverseLookup = Boolean.parseBoolean(this.properties.getProperty(sysKey));
            }
        }

        initializeFromNetwork();

        update(new MapConfigExtractor(pConfig), pResolved);

        // now initialize from sys/env to override defaults
        if (systemPropertyMode != SystemPropertyMode.NEVER) {
            initializeFromEnvironment(pResolved);
        }

        // now resolve all remaining values if needed
        if (pResolved != null) {
            pConfig.forEach((k, v) -> {
                if (!pResolved.containsKey(k)) {
                    pResolved.put(k, resolve(v));
                }
            });
        }
    }

    public Map<String, String> getNetworkConfig() {
        return Collections.unmodifiableMap(networkConfig);
    }

    private void initializeFromNetwork() {
        // check network config and populate the map with keys: ip, ip6, host, host6 and versions with :<interface-id>
        NetworkInterface best = NetworkUtil.getBestMatchNetworkInterface();
        Map<String, InetAddresses> config = NetworkUtil.getBestMatchAddresses();

        config.forEach((name, addresses) -> {
            if (NetworkUtil.isIPv6Supported() && addresses.getIa6().isPresent()) {
                Inet6Address ia6 = addresses.getIa6().get();
                String ip6Address = ia6.getHostAddress();
                if (ia6.getScopedInterface() != null || ia6.getScopeId() > 0) {
                    int percent = ip6Address.indexOf('%');
                    if (percent != -1) {
                        ip6Address = ip6Address.substring(0, percent);
                    }
                }
                if (best != null && name.equals(best.getName())) {
                    networkConfig.put("ip6", ip6Address);
                    networkConfig.put("host6", this.allowDnsReverseLookup ? ia6.getHostName() : ip6Address);
                }
                networkConfig.put("ip6:" + name, ip6Address);
                networkConfig.put("host6:" + name, this.allowDnsReverseLookup ? ia6.getHostName() : ip6Address);
            }

            if (addresses.getIa4().isPresent()) {
                Inet4Address ia4 = addresses.getIa4().get();
                if (best != null && name.equals(best.getName())) {
                    networkConfig.put("ip", ia4.getHostAddress());
                    networkConfig.put("host", this.allowDnsReverseLookup ? ia4.getHostName() : ia4.getHostAddress());
                }
                networkConfig.put("ip:" + name, ia4.getHostAddress());
                networkConfig.put("host:" + name, this.allowDnsReverseLookup ? ia4.getHostName() : ia4.getHostAddress());
            }
        });
        this.properties.putAll(networkConfig);
    }

    /**
     * Check if there are any Jolokia properties specified using environment variables and/or system properties.
     * Values are placeholder-resolved.
     */
    private void initializeFromEnvironment(Map<String, String> pResolved) {
        // properties from env which have corresponding ConfigKey
        this.environmentVariables = new HashMap<>();
        // copy of current environment
        Map<String, String> env = new HashMap<>(env());
        // sys properties which have corresponding ConfigKey
        this.systemProperties = new HashMap<>();
        // copy of current sys properties
        Map<String, String> sys = new HashMap<>();

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

        // here, during initialization time, we also override existing options (from default or
        // embedded config files) with sys/env. If user calls update() later, new properties
        // will override initial state
        update(new MapConfigExtractor(env, (k) -> k.startsWith("JOLOKIA_")), pResolved,
            ConfigKey::fromEnvVariableFormat);
        update(new MapConfigExtractor(sys, (k) -> k.startsWith("jolokia.")), pResolved,
            (key) -> key.substring("jolokia.".length()));
    }

    /**
     * Resolves any placeholder in the form of supported {@code ${xxx}} syntax - using env variables, sys properties
     * or host/host6/ip/ip6 values.
     * @param value value to resolve
     * @return resolved value
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
        update(pExtractor, null);
    }

    /**
     * Update the configuration hold by this object overriding existing values (but not the sys/env variables
     * used during initialization). The placeholder values are resolved.
     *
     * @param pExtractor an extractor for retrieving the configuration from some external object
     * @param pResolved map to collect resolved properties
     */
    public void update(ConfigExtractor pExtractor, Map<String, String> pResolved) {
        update(pExtractor, pResolved, Function.identity());
    }

    /**
     * Update the configuration hold by this object overriding existing values (but not the sys/env variables
     * used during initialization). The placeholder values are resolved.
     *
     * @param pExtractor an extractor for retrieving the configuration from some external object
     * @param pResolved map to collect resolved properties
     * @param newKey key mapper to translate key to match selected {@link ConfigExtractor}
     */
    private void update(ConfigExtractor pExtractor, Map<String, String> pResolved, Function<String, String> newKey) {
        Enumeration<String> e = pExtractor.getNames();
        while (e.hasMoreElements()) {
            String originalKey = e.nextElement();
            String changedKey = newKey.apply(originalKey);
            ConfigKey key = ConfigKey.getGlobalConfigKey(changedKey);
            String resolved = resolve(pExtractor.getParameter(originalKey));
            if (pResolved != null) {
                pResolved.put(changedKey, resolved);
            }
            if (key != null) {
                configMap.put(key, resolved);
                keys.add(key);
            }
        }
    }

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pKey) {
        String v;
        if (systemPropertyMode == SystemPropertyMode.OVERRIDE) {
            v = systemProperties.get(pKey);
            if (v != null) {
                return v;
            }
            v = environmentVariables.get(pKey);
            if (v != null) {
                return v;
            }
        }
        v = configMap.get(pKey);
        if (v != null) {
            return v;
        }
        if (systemPropertyMode == SystemPropertyMode.FALLBACK) {
            v = systemProperties.get(pKey);
            if (v != null) {
                return v;
            }
            v = environmentVariables.get(pKey);
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

    public void addSupportedAuthentication(SecurityDetails.AuthMethod method, String realm) {
        securityDetails.registerAuthenticationMethod(method, realm);
    }

    @Override
    public SecurityDetails getSecurityDetails() {
        return securityDetails;
    }

}
