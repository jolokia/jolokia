package org.jolokia.server.core.config;

/*
 * Copyright 2009-2018 Roland Huss
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
import java.util.TimeZone;

/**
 * Enumeration defining the various configuration constant names which
 * can be used to configure the agent globally (e.g. in web.xml) or
 * as processing parameters (e.g. as query params).
 *
 * @author roland
 * @author nevenr
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
    DEBUG("debug",true, false, Constants.FALSE),

    /**
     * Maximum number of debug entries to hold
     */
    DEBUG_MAX_ENTRIES("debugMaxEntries",true, false, "100"),

    /**
     * Path to a white list of patterns which are matched against possible
     * JMX service URL for incoming requests
     */
    JSR160_PROXY_ALLOWED_TARGETS("jsr160ProxyAllowedTargets", true, false),

    /**
     * Log handler class to use, which must have an empty constructor.
     * If not set, then a default logging mechanism is used.
     */
    LOGHANDLER_CLASS("logHandlerClass", true, false),

    /**
     * Log handler name to use. It's often called <em>log category</em>.
     * If not set, {@code org.jolokia} is used.
     */
    LOGHANDLER_NAME("logHandlerName", true, false, "org.jolokia"),

    /**
     * Maximum traversal depth for serialization of complex objects.
     */
    MAX_DEPTH("maxDepth",true, true),

    /**
     * Maximum size of collections returned during serialization.
     * If larger, the collection is truncated
     */
    MAX_COLLECTION_SIZE("maxCollectionSize",true, true),

    /**
     * Maximum number of objects returned by serialization
     */
    MAX_OBJECTS("maxObjects",true, true),

    /**
     * How to serialize long values: "number" or "string"
     */
    SERIALIZE_LONG("serializeLong", true, true),

    /**
     * Custom restrictor to be used instead of default one
     */
    RESTRICTOR_CLASS("restrictorClass", true, false),

    /**
     * Init parameter for the location of the policy file. This should be an URL pointing to
     * the policy file. If this URL uses a scheme <code>classpath</code> then do a class lookup.
     */
    POLICY_LOCATION("policyLocation",true,false,"classpath:/jolokia-access.xml"),

    /**
     * Whether a reverse DNS lookup is allowed or not. Reverse DNS lookups might happen for checking
     * host based restrictions, but might be costly.
     */
    ALLOW_DNS_REVERSE_LOOKUP("allowDnsReverseLookup", true, false, Constants.FALSE),

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
     * be included. Default is "false"
     */
    INCLUDE_STACKTRACE("includeStackTrace", true, true, Constants.FALSE),

    /**
     * Whether to include a JSON serialized version of the exception. If set
     * to "true", the exception is added under the key "error_value" in
     * the response. Default is false.
     */
    SERIALIZE_EXCEPTION("serializeException", true, true, Constants.FALSE),

    /**
     * Whether expose extended error information like stacktraces or serialized exception
     * at all. INCLUDE_STACKTRACE and SERIALIZE_EXCEPTION take effect only when ALLOW_ERROR_DETAILS
     * is set to true. This could be set to false to avoid exposure of internal data.
     */
    ALLOW_ERROR_DETAILS("allowErrorDetails", true, false, Constants.TRUE),

    /**
     * Whether  property keys of ObjectNames should be ordered in the canonical way or in the way that they
     * are created.
     * The allowed values are either "true" in which case the canonical key order (== alphabetical
     * sorted) is used or "false" for getting the keys as registered. Default is "true"
     */
    CANONICAL_NAMING("canonicalNaming", true, true, Constants.TRUE),

    /**
     * Whether to use streaming json responses. Default is "true"
     */
    STREAMING("streaming", true, false, Constants.TRUE),

    /**
     * Optional domain name for registering own MBeans
     */
    MBEAN_QUALIFIER("mbeanQualifier", true, false),

    /**
     * Option which can be given to a request to specify a JSONP callback.
     * The generated answer will be of type text/javascript and it will
     * contain a JavaScript function to be called.
     */
    CALLBACK("callback", false, true),

    /**
     * Mime Type to use for the response value. By default, this is
     * <code>text/plain</code>, but it could be useful to return
     * <code>application/json</code>, too. A request parameter overrides a global
     * configuration.
     */
    MIME_TYPE("mimeType", true, true, "application/json"),

    /**
     * Whether to include the incoming request in the {@code request} field of the response.
     * Be careful when corelating bulk requests/responses. Defaults to {@code true}.
     */
    INCLUDE_REQUEST("includeRequest", true, true),

    /**
     * A request parameter for {@code list} operation, which tells Jolokia to return a map of keys obtained from
     * {@link javax.management.ObjectName#getKeyPropertyList()} under {@code keys} field of the data for an MBean.
     */
    LIST_KEYS("listKeys", false, true, Constants.FALSE),

    /**
     * For LIST requests, this option can be used to return
     * the result only if they set of registered MBeans has
     * been changed since the timestamp given in this option.
     * The timestamp has to be given in seconds since 1.1.1970
     * (epoch time).
     */
    IF_MODIFIED_SINCE("ifModifiedSince",false,true),

    /**
     * Query parameter used for providing a path in order to avoid escaping
     * issues. This can be used as an alternative for path notations
     */
    PATH_QUERY_PARAM("p",false,true),

    /**
     * Whether to enable listening and responding to discovery multicast requests
     * for discovering agent details.
     */
    DISCOVERY_ENABLED("discoveryEnabled",true,false, Constants.FALSE),

    /**
     * Specify the agent URL to return for an discovery multicast request. If this option
     * is given {@link #DISCOVERY_ENABLED} is set to <code>true</code> automatically.
     */
    DISCOVERY_AGENT_URL("discoveryAgentUrl",true,false),

    /**
     * <p>IP address for Jolokia's Multicast group. For IPv4 (since always in Jolokia) we have 239.192.48.84.
     * For IPv6 we can choose an address from ffx8::/16 (IPv6 equivalent of 239.192.0.0/14). See
     * <a href="https://en.wikipedia.org/wiki/Multicast_address#IPv6">Multicast/IPv6</a>.</p>
     *
     * <p>To make UDP multicasting work on Linux, make sure firewall is properly configured. On Fedora
     * with default <code>FedoraServer</code> zone, use:
     * <pre>
     *     $ firewall-cmd --zone=FedoraServer --add-rich-rule='rule family=ipv4 destination address="239.192.48.84" accept'
     *     $ firewall-cmd --zone=FedoraServer --add-rich-rule='rule family=ipv6 destination address="ff08::48:84" accept'
     * </pre>
     * or directly using {@code nft} (chain name may vary):
     * <pre>
     *     $ nft add rule inet firewalld filter_IN_FedoraServer_allow ip daddr 239.192.48.84 accept
     *     $ nft add rule inet firewalld filter_IN_FedoraServer_allow ip6 daddr ff08::48:84 accept
     * </pre>
     * </p>
     */
    MULTICAST_GROUP("multicastGroup", true, false, "239.192.48.84"),

    /**
     * <p>Address of {@link java.net.MulticastSocket} to bind when listening for UDP mulitcast discovery requests.</p>
     *
     * <p>On IPv6 enabled hosts (or when {@code -Djava.net.preferIPv4Stack} is not {@code true}), even {@code 0.0.0.0}
     * means that the UDP {@link java.net.MulticastSocket} is bound to {@code 0:0:0:0:0:0:0:0} IPv6 address and in
     * Java this socket doesn't have IPV6_ONLY option, so it's accepting traffic with IPv4 and IPv6 protocols.</p>
     *
     * <p>Setting it to real IP address of some physical or virtual interface effectively narrows the sources of
     * incoming discovery requests.</p>
     */
    MULTICAST_BIND_ADDRESS("multicastBindAddress", true, false, "0.0.0.0"),

    /**
     * Multicast port where to listen for queries.
     */
    MULTICAST_PORT("multicastPort", true, false, "24884"),

    // ================================================================================
    // Configuration relevant for OSGI container

    /**
     *  User for authentication purposes. Used by OSGi and JDK agent.
     */
    USER("user", true, false),

    /**
     *  Password for authentication purposes. Used by OSGi and JDK agent
     */
    PASSWORD("password", true, false),

    /**
     * The security realm used for login
     */
    REALM("realm", true, false, "jolokia"),

    /**
     * What authentication to use. Support values: "basic" for basic authentication, "jaas" for
     * JaaS authentication, "delegate" for delegating to another HTTP service.
     * For OSGi agent there are the additional modes "service-all" and "service-any" to use Authenticator services
     * provided via an OSGi service registry.
     */
    AUTH_MODE("authMode", true, false, "basic"),

    /**
     * If MultiAuthenticator is used, this config item explains how to combine multiple authenticators
     * Supported values: "any" at least one authenticator must match, "all" all authenticators must match
     */
    AUTH_MATCH("authMatch",true, false, "any"),

    /**
     * Custom authenticator to be used instead of default user/password one (JVM agent)
     */
    AUTH_CLASS("authClass", true, false),

    /**
     * URL used for a dispatcher authentication (authMode == delegate)
     */
    AUTH_URL("authUrl",true,false),

    /**
     * Extractor specification for getting the principal (authMode == delegate)
     */
    AUTH_PRINCIPAL_SPEC("authPrincipalSpec",true,false),

    /**
     * Whether to ignore CERTS when doing a dispatching authentication (authMode == delegate)
     */
    AUTH_IGNORE_CERTS("authIgnoreCerts",true,false,Constants.FALSE),

    /**
     * Context used for agent, used e.g. in the OSGi activator
     * (but not for the servlet, this is done in web.xml)
     */
    AGENT_CONTEXT("agentContext", true, false, "/jolokia"),

    /**
     * For OSGi, if set to true, the agent uses a restrictor service when it kicks in,
     * but denies access otherwise.
     */
    USE_RESTRICTOR_SERVICE("useRestrictorService", true, false, Constants.FALSE),

    /**
     * By default, the OSGi Agent listens for an OSGi HttpService to which it will register
     * an agent servlet. Set this to false if you want to instantiate the
     * servlet on your own (either declaratively within another war or programmatically)
     *
     * @deprecated option is kept for compatibility reasons, because OSGi servlet is now registered
     *             using Servlet Whiteboard pattern. Please use {@link #REGISTER_WHITEBOARD_SERVLET}
     */
    @Deprecated
    LISTEN_FOR_HTTP_SERVICE("listenForHttpService", true, false),

    /**
     * By default, the OSGi Agent uses Whiteboard pattern to register an agent servlet.
     * Set this to false if you want to instantiate and register the servlet on your own.
     */
    REGISTER_WHITEBOARD_SERVLET("registerWhiteboardServlet", true, false, Constants.TRUE),

    /**
     * By default, the OSGi Agent will bind to all HttpService implementations.
     * Set this to control which of the implementations of HttpService are bound to.
     * <p>The syntax is that of the standard OSGi Filter.</p>
     * <pre><code>
     *     (VirtualServer=__asadmin)  --> Glassfish 3+ administration server only
     * </code></pre>
     * <p>Note this will be combined with the objectClass filter for HttpService with
     * the and (&amp;) operator.</p>
     *
     * @deprecated <a href="https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.servlet.html">Whiteboard
     *             specification</a> can no longer <em>point to</em> specific HttpService implementation.
     */
    @Deprecated
    HTTP_SERVICE_FILTER("httpServiceFilter",true,false),

    /**
     * With this option we can disabled usage of {@link org.jolokia.server.core.detector.ServerDetector server detectors}.
     */
    DISABLE_DETECTORS("disableDetectors", true, false, Constants.FALSE),

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
    DETECTOR_OPTIONS("detectorOptions",true, false),

    /**
     * The ID to uniquely identify this agent within a JVM. There
     * can be multiple agents registered a JVM. This id is e.g. used to
     * uniquely create MBean names.
     */
    AGENT_ID("agentId", true, false),

    /**
     * The agent type holds the information which kind of agent (war,jvm,osgi)
     * is in use. This configuration cannot be set from the outside but is
     * written by the agent itself
     */
    AGENT_TYPE("agentType", true, false),

    /**
     * A description which can be used to describe the agent further. Typically
     * this can be used by clients to provide additional information to
     * the user.
     */
    AGENT_DESCRIPTION("agentDescription",true,false),

    /**
     * Comma-separated list of fully qualified class names for services that should be enabled. If not-empty,
     * this is a enabled-list of services which narrows the list of all the services
     * detected from {@code /META-INF/jolokia/services(-default)}.
     */
    ENABLED_SERVICES("enabledServices", true, false),

    /**
     * Comma-separated list of fully qualified class names for services that should be disabled even if
     * they are detected from {@code /META-INF/jolokia/services(-default)}.
     */
    DISABLED_SERVICES("disabledServices", true, false),

    /**
     * Format for {@link java.text.SimpleDateFormat} used for {@link java.util.Date} serialization
     * and deserialization. Defaults to <a href="https://en.wikipedia.org/wiki/ISO_8601">ISO-9601 extended format</a>
     * (with {@code -} separators for year/month/day and {@code :} separator for hour/minute/second.
     * Date and Time are separated by {@code T}.
     * The option should be a valid parameter for {@link java.text.SimpleDateFormat} constructor
     * and {@link java.time.format.DateTimeFormatter#ofPattern}, but
     * we accept few special values:<ul>
     *     <li>{@code time}, {@code long}, {@code unix} and {@code millis} - Unix epoch time (milliseconds since 1970-01-01)</li>
     *     <li>{@code nanos} - Unix epoch time in nanoseconds (since 1970-01-01)</li>
     * </ul>
     */
    DATE_FORMAT("dateFormat", true, false, "yyyy-MM-dd'T'HH:mm:ssXXX"),

    /**
     * When formatting dates using {@link java.text.SimpleDateFormat} uses default (local) timeZone, but
     * we may specify another zone (for example {@code TimeZone#getTimeZone("UTC")}).
     */
    DATE_FORMAT_ZONE("dateFormatTimeZone", true, false, TimeZone.getDefault().toZoneId().getId()),

    /**
     * Processing parameter used to enable <em>smart list response</em> where JSON data for each {@link javax.management.MBeanInfo}
     * is cached instead of being duplicated for each (potentially the same) MBean of similar class.
     */
    LIST_CACHE("listCache", false, true, Constants.FALSE);

    /**
     * JAAS Subject to attach to an HTTP request as attribute if JAAS based authentication is in use.
     * This constant can only be used programtically
     */
    public static final String JAAS_SUBJECT_REQUEST_ATTRIBUTE = "org.jolokia.jaasSubject";

    private final String  key;
    private final String  defaultValue;
    private final boolean globalConfig;
    private final boolean requestConfig;

    private static final Map<String, ConfigKey> keyByName;
    private static final Map<String, ConfigKey> globalKeyByName;
    private static final Map<String, ConfigKey> requestKeyByName;

    // Build up internal reverse map
    static {
        keyByName = new HashMap<>();
        globalKeyByName = new HashMap<>();
        requestKeyByName = new HashMap<>();
        for (ConfigKey ck : ConfigKey.values()) {
            keyByName.put(ck.getKeyValue(), ck);
            if (ck.isGlobalConfig()) {
                globalKeyByName.put(ck.getKeyValue(), ck);
            }
            if (ck.isRequestConfig()) {
                requestKeyByName.put(ck.getKeyValue(), ck);
            }
        }
    }


    ConfigKey(String pValue, boolean pIsGlobalConfig, boolean pIsRequestConfig) {
        this(pValue, pIsGlobalConfig, pIsRequestConfig, null);
    }

    ConfigKey(String pValue, boolean pIsGlobalConfig, boolean pIsRequestConfig, String pDefault) {
        key = pValue;
        defaultValue = pDefault;
        globalConfig = pIsGlobalConfig;
        requestConfig = pIsRequestConfig;
    }

    /** {@inheritDoc} */
    @Override
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

    /**
     * Get the value of this key as it could be possibly used as
     * a system property to {@link System#getProperty(String)}.
     *
     * @return key, pefixed with "jolokia."
     */
    public String asSystemProperty() {
        return "jolokia." + getKeyValue();
    }

    /**
     * Get the value of this key as it could be possibly used as
     * an environment variable in {@link System#getenv(String)}. Note, that
     * only a few config values can be set that way.
     *
     * @return key, pefixed with "jolokia."
     */
    public String asEnvVariable() {
        String kevValue = getKeyValue();
        StringBuilder buf = new StringBuilder();
        boolean notFirst = false;
        for (char c : kevValue.toCharArray()) {
            if (Character.isUpperCase(c) && notFirst) {
                buf.append("_").append(c);
            } else {
                buf.append(Character.toUpperCase(c));
            }
            notFirst = true;
        }
        return "JOLOKIA_" + buf;
    }


    // Constants used for boolean values
    private static class Constants {
        public static final String FALSE = "false";
        public static final String TRUE = "true";
    }
}
