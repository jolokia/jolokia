package org.jolokia.jvmagent;

/*
 * Copyright 2009-2014 Roland Huss
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.Authenticator;
import org.jolokia.jvmagent.security.*;
import org.jolokia.server.core.config.*;
import org.jolokia.server.core.osgi.security.JaasAuthenticator;

/**
 * Configuration required for the JolokiaServer
 *
 * @author roland
 * @author nevenr
 * @since 28.12.12
 */
public class JolokiaServerConfig {

    // Jolokia configuration is used for general jolokia config, the untyped configuration
    // is used for this agent only
    private Configuration jolokiaConfig;

    private String        protocol;
    private int           port;
    private int           backlog;
    private InetAddress   address;
    private String        executor;
    private int           threadNr;
    private String        keystore;
    private String        context;
    private boolean       useSslClientAuthentication;
    private char[]        keystorePassword;
    private Authenticator authenticator;
    private String secureSocketProtocol;
    private String keyManagerAlgorithm;
    private String trustManagerAlgorithm;
    private String keyStoreType;
    private String caCert;
    private String serverCert;
    private String serverKey;
    private String serverKeyAlgorithm;
    private List<String> clientPrincipals;
    private boolean extendedClientCheck;

    /**
     * Constructor which prepares the server configuration from a map
     * of given config options (key: option name, value: option value).
     * Also, default values are used for any
     * parameter not provided ({@link #getDefaultConfig()}).
     *
     * The given configuration consist of two parts: Any global options
     * as defined in {@link ConfigKey} are used for setting up the agent.
     * All other options are taken for preparing the HTTP server under
     * which the agent is served. The known properties are described in
     * the reference manual.
     *
     * All other options are ignored.
     *
     * @param pConfig the configuration options to use.
     */
    public JolokiaServerConfig(Map<String, String> pConfig) {
        init(pConfig,getDefaultConfig());
    }

    /**
     * Empty constructor useful for subclasses which want to do their own initialization. Note that
     * the subclass must call {@link #init} on its own.
     */
    protected JolokiaServerConfig() { }

    /**
     * Initialization
     *
     * @param pConfig original config
     * @param pDefaultConfig default config used as background
     */
    protected final void init(Map<String, String> pConfig,Map<String,String> pDefaultConfig) {
        Map<String, String> finalCfg = new HashMap<String, String>(pDefaultConfig);
        finalCfg.putAll(pConfig);

        prepareDetectorOptions(finalCfg);
        addJolokiaId(finalCfg);

        jolokiaConfig = new StaticConfiguration(finalCfg);
        initConfigAndValidate(finalCfg);
    }

    // Add a unique jolokia id for this agent
    private void addJolokiaId(Map<String, String> pFinalCfg) {
        if (!pFinalCfg.containsKey(ConfigKey.AGENT_ID.getKeyValue())) {
            String id = Integer.toHexString(hashCode()) + "-jvm";
            pFinalCfg.putAll(Collections.singletonMap(ConfigKey.AGENT_ID.getKeyValue(),
                                                      id));
        }
    }

    /**
     * Read in the default configuration from a properties resource
     * @return
     */
    protected final Map<String, String> getDefaultConfig() {
        InputStream is = getClass().getResourceAsStream("/default-jolokia-agent.properties");
        return readPropertiesFromInputStream(is, "default-jolokia-agent.properties");
    }

    /**
     * Get the Jolokia runtime configuration
     * @return jolokia configuration
     */
    public Configuration getJolokiaConfig() {
        return jolokiaConfig;
    }

    /**
     * Protocol to use
     *
     * @return protocol either 'http' or 'https'
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Address to bind to, which is either used from the configuration option
     * "host" or by default from {@link InetAddress#getLocalHost()}
     *
     * @return the host's address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Port for the server to listen to
     *
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * Return a basic authenticator if user or password is given in the configuration. You can override
     * this method if you want to provide an own authenticator.
     *
     * @return an authenticator if authentication is switched on, or null if no authentication should be used.
     */
    public Authenticator getAuthenticator() {
        return authenticator;
    }

    /**
     * Backlog of the HTTP server, which is the number of requests to keep before throwing them away
     * @return backlog
     */
    public int getBacklog() {
        return backlog;
    }

    /**
     * Context path under which the agent is reachable. This path will always end with a "/" for technical
     * reasons.
     *
     * @return the context path.
     */
    public String getContextPath() {
        return context;
    }

    /**
     * Executor to use as provided by the 'executor' option or "single" as default
     * @return the executor model ("fixed", "single" or "cached")
     */
    public String getExecutor() {
        return executor;
    }

    /**
     * Thread number to use when executor model is "fixed"
     * @return number of fixed threads
     */
    public int getThreadNr() {
        return threadNr;
    }

    /**
     * When the protocol is 'https' then this property indicates whether SSL client certificate
     * authentication should be used or not
     *
     * @return true when ssl client authentication should be used
     */
    public boolean useSslClientAuthentication() {
        return useSslClientAuthentication;
    }

    /**
     * Name of the keystore for 'https', if any
     * @return name of keystore.
     */
    public String getKeystore() {
        return keystore;
    }

    /**
     * Password for keystore if a keystore is used. If not given, no password is assumed. If certs are not
     * loaded from a keystore but from PEM files directly, then this password is used for the private
     * server key
     *
     * @return the keystore password as char array or an empty array of no password is given
     */
    public char[] getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * Get a path to a CA PEM file which is used to verify client certificates. This path
     * is only used when {@link #getKeystore()} is not set.
     *
     * @return the file path where the ca cert is located.
     */
    public String getCaCert() {
        return caCert;
    }

    /**
     * Get the path to a server cert which is presented clients when using TLS.
     * This is only used when {@link #getKeystore()} is not set.
     *
     * @return the file path where the server cert is located.
     */
    public String getServerCert() {
        return serverCert;
    }

    /**
     * Get the path to a the cert which has the private server key.
     * This is only used when {@link #getKeystore()} is not set.
     *
     * @return the file path where the private server cert is located.
     */
    public String getServerKey() {
        return serverKey;
    }

    /**
     * The algorithm to use for extracting the private server key.
     *
     * @return the server keyl algoritm
     */
    public String getServerKeyAlgorithm() {
        return serverKeyAlgorithm;
    }

    // Initialise and validate early in order to fail fast in case of an configuration error
    protected void initConfigAndValidate(Map<String,String> agentConfig) {
        initContext();
        initAuthenticator();
        initProtocol(agentConfig);
        initAddress(agentConfig);
        port = Integer.parseInt(agentConfig.get("port"));
        backlog = Integer.parseInt(agentConfig.get("backlog"));
        initExecutor(agentConfig);
        initThreadNr(agentConfig);
        initHttpsRelatedSettings(agentConfig);
    }

    private void initAuthenticator() {
        initCustomAuthenticator();
        if (authenticator == null) {
            initAuthenticatorFromAuthMode();
        }
    }

    private void initCustomAuthenticator() {
        String authenticatorClass = jolokiaConfig.getConfig(ConfigKey.AUTH_CLASS);

        if (authenticatorClass != null) {
            Class authClass = getAuthenticatorClass(authenticatorClass);

            try {
                // prefer constructor that takes configuration
                authenticator = createFromConstructorWithConfigArg(authClass);
            } catch (NoSuchMethodException ignore) {
                // fallback to default constructor
                authenticator = createFromDefaultConstructor(authClass);
            }
        }
    }

    private Authenticator createFromConstructorWithConfigArg(Class pAuthClass) throws NoSuchMethodException {
        try {
            Constructor constructorThatTakesConfiguration = pAuthClass.getConstructor(Configuration.class);
            return (Authenticator) constructorThatTakesConfiguration.newInstance(this.jolokiaConfig);
        }
        catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot invoke 1-arg constructor for custom authenticator " + pAuthClass, e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class " + pAuthClass, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot access 1-arg constructor for custom authenticator class" + pAuthClass +
                                               " (is the constructor 'private' ?)", e);
        }
    }

    private Authenticator createFromDefaultConstructor(Class pAuthClass) {
        try {
            Constructor defaultConstructor = pAuthClass.getConstructor();
            return (Authenticator) defaultConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class, " +
                                               "no default constructor available for " + pAuthClass, e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot invoke default constructor for custom authenticator " + pAuthClass, e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class " + pAuthClass, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot access default constructor for custom authenticator class" + pAuthClass +
                                               " (is the constructor 'private' ?)", e);
        }
    }

    private Class getAuthenticatorClass(String pAuthenticatorClass) {
        try {
            Class authClass = Class.forName(pAuthenticatorClass);
            if (!Authenticator.class.isAssignableFrom(authClass)) {
                throw new IllegalArgumentException("Provided authenticator class [" + pAuthenticatorClass +
                                                   "] is not a subclass of Authenticator");
            }
            return authClass;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot find authenticator class", e);
        }
    }

    private void initAuthenticatorFromAuthMode() {
        String user = jolokiaConfig.getConfig(ConfigKey.USER);
        String password = jolokiaConfig.getConfig(ConfigKey.PASSWORD);

        String authMode = jolokiaConfig.getConfig(ConfigKey.AUTH_MODE);
        String realm = jolokiaConfig.getConfig(ConfigKey.REALM);
        if ("basic".equalsIgnoreCase(authMode)) {
            if (user != null) {
                if (password == null) {
                    throw new IllegalArgumentException("'password' must be set if a 'user' (here: '" + user + "') is given");
                }
                authenticator = new UserPasswordHttpAuthenticator(realm,user,password);
            } else {
                authenticator = null;
            }
        } else if ("jaas".equalsIgnoreCase(authMode)) {
            authenticator = new JaasHttpAuthenticator(realm);
        } else if ("delegate".equalsIgnoreCase(authMode)) {
            String ignoreCerts = jolokiaConfig.getConfig(ConfigKey.AUTH_IGNORE_CERTS);
            authenticator = new DelegatingAuthenticator(realm,
                                                        jolokiaConfig.getConfig(ConfigKey.AUTH_URL),
                                                        jolokiaConfig.getConfig(ConfigKey.AUTH_PRINCIPAL_SPEC),
                                                        Boolean.getBoolean(ignoreCerts));

        } else {
            throw new IllegalArgumentException("No auth method '" + authMode + "' known. " +
                                               "Must be either 'basic' or 'jaas'");
        }
    }

    private void initProtocol(Map<String, String> agentConfig) {
        protocol = agentConfig.containsKey("protocol") ? agentConfig.get("protocol") : "http";
        if (!protocol.equals("http") && !protocol.equals("https")) {
            throw new IllegalArgumentException("Invalid protocol '" + protocol + "'. Must be either 'http' or 'https'");
        }
    }

    private void initContext() {
        context = jolokiaConfig.getConfig(ConfigKey.AGENT_CONTEXT);
        if (context == null) {
            context = ConfigKey.AGENT_CONTEXT.getDefaultValue();
        }
        if (!context.endsWith("/")) {
            context += "/";
        }
    }

    private void initHttpsRelatedSettings(Map<String, String> agentConfig) {
        // keystore
        keystore = agentConfig.get("keystore");
        caCert = agentConfig.get("caCert");
        serverCert = agentConfig.get("serverCert");
        serverKey = agentConfig.get("serverKey");

        secureSocketProtocol = agentConfig.get("secureSocketProtocol");
        keyStoreType = agentConfig.get("keyStoreType");
        keyManagerAlgorithm = agentConfig.get("keyManagerAlgorithm");
        trustManagerAlgorithm = agentConfig.get("trustManagerAlgorithm");

        String auth = agentConfig.get("useSslClientAuthentication");
        useSslClientAuthentication = auth != null && Boolean.valueOf(auth);

        String password = agentConfig.get("keystorePassword");
        keystorePassword =  password != null ? decipherPasswordIfNecessary(password) : new char[0];

        serverKeyAlgorithm = agentConfig.get("serverKeyAlgorithm");
        clientPrincipals = extractList(agentConfig,"clientPrincipal");
        String xCheck = agentConfig.get("extendedClientCheck");
        extendedClientCheck = xCheck != null && Boolean.valueOf(xCheck);
    }

    private char[] decipherPasswordIfNecessary(String password) {
        Matcher encryptedPasswordMatcher = Pattern.compile("^\\[\\[(.*)]]$").matcher(password);
        if (encryptedPasswordMatcher.matches()) {
            String encryptedPassword = encryptedPasswordMatcher.group(1);
            try {
                JolokiaCipher jolokiaCipher = new JolokiaCipher();
                return jolokiaCipher.decrypt(encryptedPassword).toCharArray();
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Cannot decrypt password " + encryptedPassword);
            }
        } else {
            return password.toCharArray();
        }
    }

    // Extract list from multiple string entries. <code>null</code> if no such config is given
    // The first element is one without extensions
    // More elements can be given with ".1", ".2", ... added.
    private List<String> extractList(Map<String, String> pAgentConfig, String pKey) {
        List<String> ret = new ArrayList<String>();
        if (pAgentConfig.containsKey(pKey)) {
            ret.add(pAgentConfig.get(pKey));
        }
        int idx = 1;
        String keyIdx = pKey + "." + idx;
        while (pAgentConfig.containsKey(keyIdx)) {
            ret.add(pAgentConfig.get(keyIdx));
            keyIdx = pKey + "." + ++idx;
        }
        return ret.size() > 0 ? ret : null;
    }

    private void initThreadNr(Map<String, String> pAgentConfig) {
        // Thread-Nr
        String threadNrS =  pAgentConfig.get("threadNr");
        threadNr = threadNrS != null ? Integer.parseInt(threadNrS) : 5;
    }

    private void initExecutor(Map<String, String> agentConfig) {
        executor = agentConfig.containsKey("executor") ? agentConfig.get("executor") : "single";
        if (!"single".equalsIgnoreCase(executor) &&
                !"fixed".equalsIgnoreCase(executor) &&
                !"cached".equalsIgnoreCase(executor)) {
            throw new IllegalArgumentException("Executor model can be '" + executor +
                                               "' but most be either 'single', 'fixed' or 'cached'");
        }
    }

    private void initAddress(Map<String, String> agentConfig) {
        String host = agentConfig.get("host");
        try {
            if ("*".equals(host) || "0.0.0.0".equals(host)) {
                address = null; // null is the wildcard
            } else if (host != null) {
                address = InetAddress.getByName(host); // some specific host
            } else {
                address = InetAddress.getByName(null); // secure alternative -- if no host, use *loopback*
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Can not lookup " + (host != null ? host : "loopback interface") + ": " + e,e);
        }
    }

    protected final Map<String, String> readPropertiesFromInputStream(InputStream pIs, String pLabel) {
        Map ret = new HashMap<String, String>();
        if (pIs == null) {
            return ret;
        }
        Properties props = new Properties();
        try {
            props.load(pIs);
            ret.putAll(props);
        } catch (IOException e) {
            throw new IllegalArgumentException("jolokia: Cannot load properties " + pLabel + " : " + e,e);
        }
        return ret;
    }

    // Add detector specific options if given on the command line
    private void prepareDetectorOptions(Map<String, String> pConfig) {
        StringBuffer detectorOpts = new StringBuffer("{");
        if (pConfig.containsKey("bootAmx") && Boolean.parseBoolean(pConfig.get("bootAmx"))) {
            detectorOpts.append("\"glassfish\" : { \"bootAmx\" : true }");
        }
        if (detectorOpts.length() > 1) {
            detectorOpts.append("}");
            pConfig.put(ConfigKey.DETECTOR_OPTIONS.getKeyValue(),detectorOpts.toString());
        }
    }

    public String getSecureSocketProtocol() {
        return secureSocketProtocol;
    }

    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }

    public String getTrustManagerAlgorithm() {
        return trustManagerAlgorithm;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public List<String> getClientPrincipals() {
        return clientPrincipals;
    }

    public boolean getExtendedClientCheck() {
        return extendedClientCheck;
    }
}
