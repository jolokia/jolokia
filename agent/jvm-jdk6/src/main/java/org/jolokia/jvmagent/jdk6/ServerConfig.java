package org.jolokia.jvmagent.jdk6;

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

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.jolokia.util.ConfigKey;
import org.jolokia.util.StringUtil;

/**
 * Holds all Http-Server and Jolokia configuration.
 *
 * Default values are first loaded from the <code>jolokia-agent.properties</code>
 * from the class path (top-level). All default values are defined within this file.
 *
 * @author roland
 * @since 13.08.11
 */
public class ServerConfig {

    // Jolokia configuration is used for general jolokia config, the untyped configuration
    // is used for this agent only
    private Map<String,String> agentConfig;
    private Map<ConfigKey,String> jolokiaConfig;

    // Validated properties
    private boolean isStopMode;
    private String protocol;
    private int port;
    private int backlog;
    private InetAddress address;
    private String executor;
    private int threadNr;
    private String keystore;
    private String context;

    /**
     * Constructor which parser an agent argument string
     *
     * @param pArgs arguments glued together as provided on the commandline
     *        for an agent parameter
     */
    public ServerConfig(String pArgs) {
        agentConfig = parseArgs(pArgs);
        jolokiaConfig = ConfigKey.extractConfig(agentConfig);

        initialiseAndValidate();
    }

    /**
     * Get the Jolokia runtime configuration
     * @return jolokia configuration
     */
    public Map<ConfigKey, String> getJolokiaConfig() {
        return jolokiaConfig;
    }

    /**
     * The mode is 'stop' indicates that the server should be stopped when used in dynamic mode
     * @return the running mode
     */
    public boolean isModeStop() {
        return isStopMode;
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
     * User name or <code>null</code> if no authentication should be used
     * @return user
     */
    public String getUser() {
        return jolokiaConfig.get(ConfigKey.USER);
    }

    /**
     * Password to be used when authentication is switched on. If <code>user</code> is set, then
     * <code>password</code> must be set, too.
     * @return password
     */
    public String getPassword() {
        return jolokiaConfig.get(ConfigKey.PASSWORD);
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

    public boolean useClientAuthentication() {
        String auth = agentConfig.get("useSslClientAuthentication");
        return auth != null && Boolean.getBoolean(auth);
    }

    /**
     * Name of the keystore for 'https', if any
     * @return name of keystore.
     */
    public String getKeystore() {
        return keystore;
    }

    /**
     * Password for keystore if a keystore is used. If not given, no password is assumed.
     *
     * @return the keystore password as char array or an empty array of no password is given
     */
    public char[] getKeystorePassword() {
        String password = agentConfig.get("keystorePassword");
        return password != null ? password.toCharArray() : new char[0];
    }

    // ==========================================================================================================

    // Initialise and validate early in order to fail fast in case of an configuration error
    private void initialiseAndValidate() {
        initMode();
        initContext();
        initProtocol();
        initAddress();
        port = Integer.parseInt(agentConfig.get("port"));
        backlog = Integer.parseInt(agentConfig.get("backlog"));
        initExecutor();
        initThreadNr();
        initKeystore();
    }

    private void initMode() {
        String mode = agentConfig.get("mode");
        if (mode != null && !mode.equals("start") && !mode.equals("stop")) {
            throw new IllegalArgumentException("Invalid running mode '" + mode + "'. Must be either 'start' or 'stop'");
        }
        isStopMode = "stop".equals(mode);
    }

    private void initProtocol() {
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


    private void initKeystore() {
        // keystore
        keystore = agentConfig.get("keystore");
        if (protocol.equals("https") && keystore == null) {
            throw new IllegalArgumentException("No keystore defined for HTTPS protocol. " +
                                               "Please use the 'keystore' option to point to a valid keystore");
        }
    }

    private void initThreadNr() {
        // Thread-Nr
        String threadNrS =  agentConfig.get("threadNr");
        threadNr = threadNrS != null ? Integer.parseInt(threadNrS) : 5;
    }

    private void initExecutor() {
        executor = agentConfig.containsKey("executor") ? agentConfig.get("executor") : "single";
        if (!"single".equalsIgnoreCase(executor) &&
                !"fixed".equalsIgnoreCase(executor) &&
                !"cached".equalsIgnoreCase(executor)) {
            throw new IllegalArgumentException("Executor model can be '" + executor +
                                               "' but most be either 'single', 'fixed' or 'cached'");
        }
    }

    private void initAddress() {
        String host = agentConfig.get("host");
        try {
            address = host != null ? InetAddress.getByName(host) : InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Can not lookup " + (host != null ? host : "localhost") + ": " + e,e);
        }
    }



    // ======================================================================================
    // Parse argument

    private Map<String, String> parseArgs(String pAgentArgs) {
        Map<String,String> ret = new HashMap<String, String>();
        if (pAgentArgs != null && pAgentArgs.length() > 0) {
            for (String arg : StringUtil.splitAsArray(pAgentArgs,StringUtil.CSV_ESCAPE,",")) {
                String[] prop = StringUtil.splitAsArray(arg,StringUtil.CSV_ESCAPE,"=");
                if (prop == null || prop.length != 2) {
                    throw new IllegalArgumentException("jolokia: Invalid option '" + arg + "'. Ignoring");
                } else {
                    ret.put(prop[0],prop[1]);
                }
            }
        }
        Map<String,String> config = getDefaultConfig();
        if (ret.containsKey("config")) {
            Map<String,String> userConfig = readConfig(ret.get("config"));
            config.putAll(userConfig);
            config.putAll(ret);
            return config;
        } else {
            config.putAll(ret);
            return config;
        }
    }

    private Map<String, String> readConfig(String pFilename) {
        File file = new File(pFilename);
        try {
            InputStream is = new FileInputStream(file);
            return readPropertiesFromInputStream(is,pFilename);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("jolokia: Can not find configuration file " + pFilename,e);
        }
    }

    private Map<String, String> getDefaultConfig() {
        InputStream is =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("jolokia-agent.properties");
        return readPropertiesFromInputStream(is,"jolokia-agent.properties");
    }

    private Map<String, String> readPropertiesFromInputStream(InputStream pIs,String pLabel) {
        Map ret = new HashMap<String, String>();
        if (pIs == null) {
            return ret;
        }
        Properties props = new Properties();
        try {
            props.load(pIs);
            ret.putAll(props);
        } catch (IOException e) {
            throw new IllegalArgumentException("jolokia: Cannot load default properties " + pLabel + " : " + e,e);
        }
        return ret;
    }


}
