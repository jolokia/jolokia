package org.jolokia.jvmagent;

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
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.jvmagent.security.*;
import org.jolokia.util.JolokiaCipher;
import org.jolokia.util.NetworkUtil;

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
    private String        threadNamePrefix;
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
    private String[] sslProtocols;
    private String[] sslCipherSuites;

    /**
     * Constructor which prepares the server configuration from a map
     * of given config options (key: option name, value: option value).
     * Also, default values are used for any
     * parameter not provided ({@link #getDefaultConfig(Map)}).
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
        init(pConfig);
    }

    /**
     * Initialize the configuration with the given map
     *
     * @param pConfig map holding the configuration in string representation. A reference to the map will be kept
     */
    protected void init(Map<String, String> pConfig) {
        Map<String, String> finalCfg = getDefaultConfig(pConfig);
        finalCfg.putAll(pConfig);

        prepareDetectorOptions(finalCfg);
        addJolokiaId(finalCfg);

        jolokiaConfig = new Configuration();
        jolokiaConfig.updateGlobalConfiguration(finalCfg);
        initConfigAndValidate(finalCfg);
    }

    // Add a unique jolokia id for this agent
    private void addJolokiaId(Map<String, String> pFinalCfg) {
        if (!pFinalCfg.containsKey(ConfigKey.AGENT_ID.getKeyValue())) {
            pFinalCfg.put(ConfigKey.AGENT_ID.getKeyValue(), NetworkUtil.getAgentId(hashCode(),"jvm"));
        }
        pFinalCfg.put(ConfigKey.AGENT_TYPE.getKeyValue(), "jvm");
    }

    protected Map<String, String> getDefaultConfig(Map<String,String> pConfig) {
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
     * Whether or not to use https as the procol
     *
     * @return true when using https as the protocol
     */
    public boolean useHttps() {
        return protocol.equalsIgnoreCase("https");
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
     * Thread name prefix that executor will use while creating new thread(s).
     * @return the thread(s) name prefix
     */
    public String getThreadNamePrefix() {
        return threadNamePrefix;
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
     * @return the server key algorithm
     */
    public String getServerKeyAlgorithm() {
        return serverKeyAlgorithm;
    }

    /**
     * The list of enabled SSL / TLS protocols to serve with
     *
     * @return the list of enabled protocols
     */
    public String[] getSSLProtocols() { return sslProtocols; }

    /**
     * The list of enabled SSL / TLS cipher suites
     *
     * @return the list of cipher suites
     */
    public String[] getSSLCipherSuites() {
        return sslCipherSuites;
    }

    /**
     * Filter the list of protocols and ciphers to those supported by the given SSLContext
     *
     * @param sslContext the SSLContext to pull information from
     */
    public void updateHTTPSSettingsFromContext(SSLContext sslContext) {
        SSLParameters parameters = sslContext.getSupportedSSLParameters();

        // Protocols
        if (sslProtocols == null) {
            sslProtocols = parameters.getProtocols();
        } else {
            List<String> supportedProtocols = Arrays.asList(parameters.getProtocols());
            List<String> sslProtocolsList = new ArrayList<String>(Arrays.asList(sslProtocols));

            Iterator<String> pit = sslProtocolsList.iterator();
            while (pit.hasNext()) {
                String protocol = pit.next();
                if (!supportedProtocols.contains(protocol)) {
                    System.out.println("Jolokia: Discarding unsupported protocol: " + protocol);
                    pit.remove();
                }
            }
            sslProtocols = sslProtocolsList.toArray(new String[0]);
        }

        // Cipher Suites
        if (sslCipherSuites == null) {
            sslCipherSuites = parameters.getCipherSuites();
        } else {
            List<String> supportedCipherSuites = Arrays.asList(parameters.getCipherSuites());
            List<String> sslCipherSuitesList = new ArrayList<String>(Arrays.asList(sslCipherSuites));

            Iterator<String> cit = sslCipherSuitesList.iterator();
            while (cit.hasNext()) {
                String cipher = cit.next();
                if (!supportedCipherSuites.contains(cipher)) {
                    System.out.println("Jolokia: Discarding unsupported cipher suite: " + cipher);
                    cit.remove();
                }
            }
            sslCipherSuites = sslCipherSuitesList.toArray(new String[0]);
        }
    }

    // Initialise and validate early in order to fail fast in case of an configuration error
    protected void initConfigAndValidate(Map<String,String> agentConfig) {
        initContext();
        initProtocol(agentConfig);
        initAddress(agentConfig);
        port = Integer.parseInt(agentConfig.get("port"));
        backlog = Integer.parseInt(agentConfig.get("backlog"));
        initExecutor(agentConfig);
        initThreadNamePrefix(agentConfig);
        initThreadNr(agentConfig);
        initHttpsRelatedSettings(agentConfig);
        initAuthenticator();
    }

    private void initAuthenticator() {
        initCustomAuthenticator();
        if (authenticator == null) {
            initAuthenticatorFromAuthMode();
        }
    }

    private void initCustomAuthenticator() {
        String authenticatorClass = jolokiaConfig.get(ConfigKey.AUTH_CLASS);

        if (authenticatorClass != null) {
            try {
                Class authClass = Class.forName(authenticatorClass);
                if (!Authenticator.class.isAssignableFrom(authClass)) {
                    throw new IllegalArgumentException("Provided authenticator class [" + authenticatorClass +
                                                       "] is not a subclass of Authenticator");
                }
                lookupAuthenticator(authClass);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Cannot find authenticator class", e);
            }
        }
    }

    private void lookupAuthenticator(Class pAuthClass) {
        try {
            // prefer constructor that takes configuration
            try {
                Constructor constructorThatTakesConfiguration = pAuthClass.getConstructor(Configuration.class);
                authenticator = (Authenticator) constructorThatTakesConfiguration.newInstance(this.jolokiaConfig);
            } catch (NoSuchMethodException ignore) {
                // Next try
                authenticator = lookupAuthenticatorWithDefaultConstructor(pAuthClass, ignore);
            } catch (InvocationTargetException e) {
                throw new IllegalArgumentException("Cannot create an instance of custom authenticator class with configuration", e);
            }
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class", e);
        }
    }

    private Authenticator lookupAuthenticatorWithDefaultConstructor(Class pAuthClass, NoSuchMethodException ignore) throws InstantiationException, IllegalAccessException {
        // fallback to default constructor
        try {
            Constructor defaultConstructor = pAuthClass.getConstructor();
            return (Authenticator) defaultConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            e.initCause(ignore);
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class, no default constructor to use", e);
        } catch (InvocationTargetException e) {
            e.initCause(ignore);
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator using default constructor", e);
        }
    }

    private void initAuthenticatorFromAuthMode() {
        String user = jolokiaConfig.get(ConfigKey.USER);
        String password = jolokiaConfig.get(ConfigKey.PASSWORD);

        String authMode = jolokiaConfig.get(ConfigKey.AUTH_MODE);
        String realm = jolokiaConfig.get(ConfigKey.REALM);

        ArrayList<Authenticator> authenticators = new ArrayList<Authenticator>();

        if (useHttps() && useSslClientAuthentication()) {
            authenticators.add(new ClientCertAuthenticator(this));
        }

        if ("basic".equalsIgnoreCase(authMode)) {
            if (user != null) {
                if (password == null) {
                    throw new IllegalArgumentException("'password' must be set if a 'user' (here: '" + user + "') is given");
                }

                authenticators.add(new UserPasswordAuthenticator(realm,user,password));
            }
        } else if ("jaas".equalsIgnoreCase(authMode)) {
            authenticators.add(new JaasAuthenticator(realm));
        } else if ("delegate".equalsIgnoreCase(authMode)) {
            authenticators.add(new DelegatingAuthenticator(realm,
                                                        jolokiaConfig.get(ConfigKey.AUTH_URL),
                                                        jolokiaConfig.get(ConfigKey.AUTH_PRINCIPAL_SPEC),
                                                        jolokiaConfig.getAsBoolean(ConfigKey.AUTH_IGNORE_CERTS)));
        } else {
            throw new IllegalArgumentException("No auth method '" + authMode + "' known. " +
                                               "Must be either 'basic' or 'jaas'");
        }

        if (authenticators.isEmpty()) {
            authenticator = null;
        } else if (authenticators.size() == 1) {
            authenticator = authenticators.get(0);
        } else {
            // Multiple auth strategies were configured, pass auth if any of them
            // succeed.
            authenticator = new MultiAuthenticator(MultiAuthenticator.Mode.fromString(jolokiaConfig.get(ConfigKey.AUTH_MATCH)), authenticators);
        }
    }

    private void initProtocol(Map<String, String> agentConfig) {
        protocol = agentConfig.containsKey("protocol") ? agentConfig.get("protocol") : "http";
        if (!protocol.equals("http") && !protocol.equals("https")) {
            throw new IllegalArgumentException("Invalid protocol '" + protocol + "'. Must be either 'http' or 'https'");
        }
    }

    private void initContext() {
        context = jolokiaConfig.get(ConfigKey.AGENT_CONTEXT);
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
        clientPrincipals = extractList(agentConfig, "clientPrincipal");
        String xCheck = agentConfig.get("extendedClientCheck");
        extendedClientCheck = xCheck != null && Boolean.valueOf(xCheck);

        List<String> sslProtocolsList = extractList(agentConfig, "sslProtocol");
        if (sslProtocolsList != null) {
            sslProtocols = sslProtocolsList.toArray(new String[0]);
        }
        List<String> sslCipherSuitesList = extractList(agentConfig, "sslCipherSuite");
        if (sslCipherSuitesList != null) {
            sslCipherSuites = sslCipherSuitesList.toArray(new String[0]);
        }
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
            throw new IllegalArgumentException("Invalid executor model: '" + executor +
                                               "'. Must be either 'single', 'fixed' or 'cached'");
        }
    }

    private void initThreadNamePrefix(Map<String, String> agentConfig) {
        threadNamePrefix = agentConfig.get("threadNamePrefix");
        if (threadNamePrefix == null || threadNamePrefix.isEmpty()) {
            threadNamePrefix = "jolokia-";
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

    protected Map<String, String> readPropertiesFromInputStream(InputStream pIs, String pLabel) {
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
    protected void prepareDetectorOptions(Map<String, String> pConfig) {
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
