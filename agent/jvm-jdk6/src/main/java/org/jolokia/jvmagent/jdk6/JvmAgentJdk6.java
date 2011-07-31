package org.jolokia.jvmagent.jdk6;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

import com.sun.net.httpserver.*;
import org.jolokia.util.ConfigKey;

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
 * A JVM level agent using the JDK6 HTTP Server {@link com.sun.net.httpserver.HttpServer}
 *
 * Beside the configuration defined in {@link ConfigKey}, this agent honors the following
 * additional configuration keys:
 *
 * <ul>
 *  <li><strong>host</strong> : Host address to bind to
 *  <li><strong>port</strong> : Port to listen on
 *  <li><strong>backlog</strong> : max. nr of requests queued up before they get rejected
 *  <li><strong>config</strong> : path to a properties file containing configuration
 * </ul>
 *
 * Configuration will be also looked up from a properties file found in the class path as
 * <code>/jolokia-agent.properties</code>
 *
 * All configurations will be merged in the following order with the later taking precedence:
 *
 * <ul>
 *   <li>Default properties from <code>/jolokia-agent.properties<code>
 *   <li>Configuration from a config file (if given)
 *   <li>Options given on the command line in the form
 *       <code>-javaagent:agent.jar=key1=value1,key2=value2...</code>
 * </ul>
 * @author roland
 * @since Mar 3, 2010
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class JvmAgentJdk6 {

    public static final int DEFAULT_PORT = 8778;
    public static final int DEFAULT_BACKLOG = 10;
    public static final String JOLOKIA_CONTEXT = "/jolokia/";
    public static final String DEFAULT_PROTOCOL = "http";
    private static CleanUpThread cleaner = null;
    public static final String JOLOKIA_AGENT_URL = "jolokia.agent";

    private JvmAgentJdk6() {}

    /**
     * Entry point for the agent, using command line attach
     * (that is via -javagent command line argument)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void premain(String agentArgs) {
        try {
            startAgent(parseArgs(agentArgs));
        } catch(IOException ioe) {
            System.err.println("Jolokia: Cannot create HTTP-Server: " + ioe);
        }
    }

    /**
     * Entry point for the agent, using dynamic attach
     * (this is post VM initialisation attachment, via com.sun.attach)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void agentmain(String agentArgs) {
        try {
            Map<String,String> agentConfig = parseArgs(agentArgs);
            if ("stop".equals(agentConfig.get("mode"))) {
                System.clearProperty(JOLOKIA_AGENT_URL);
                System.out.println("Jolokia: Agent stopped");
                stopAgent();
            } else {
                startAgent(agentConfig);
            }
        } catch (IOException ioe) {
            System.err.println("Jolokia: Error starting agent: " + ioe);
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void startAgent(Map<String, String> agentConfig) throws IOException {
        final HttpServer server = createServer(agentConfig);

        final Map<ConfigKey,String> jolokiaConfig = ConfigKey.extractConfig(agentConfig);
        final String contextPath = getContextPath(jolokiaConfig);

        HttpContext context = server.createContext(contextPath, new JolokiaHttpHandler(jolokiaConfig));
        if (jolokiaConfig.containsKey(ConfigKey.USER)) {
            context.setAuthenticator(getAuthentiator(jolokiaConfig));
        }
        if (agentConfig.containsKey("executor")) {
            server.setExecutor(getExecutor(agentConfig));
        }
        startServer(server);
    }

    private static HttpServer createServer(Map<String, String> pConfig) throws IOException {
        int port = DEFAULT_PORT;
        if (pConfig.get("port") != null) {
            port = Integer.parseInt(pConfig.get("port"));
        }
        InetAddress address;
        if (pConfig.get("host") != null) {
            address = InetAddress.getByName(pConfig.get("host"));
        } else {
            address = InetAddress.getLocalHost();
        }

        if (!pConfig.containsKey(ConfigKey.AGENT_CONTEXT.getKeyValue())) {
            pConfig.put(ConfigKey.AGENT_CONTEXT.getKeyValue(), JOLOKIA_CONTEXT);
        }
        String protocol = DEFAULT_PROTOCOL;
        if (pConfig.get("protocol") != null) {
            protocol = pConfig.get("protocol");
        }
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);

        HttpServer toReturn = null;
        if (protocol.equalsIgnoreCase("https")) {
            toReturn = createHttpsServer(socketAddress, pConfig);
        } else {
            toReturn = HttpServer.create(socketAddress,getBacklog(pConfig));
        }

        String url = String.format("%s://%s:%d%s",
            "https".equalsIgnoreCase(protocol) ? "https" : "http",
            address.getCanonicalHostName(), port,
            pConfig.get(ConfigKey.AGENT_CONTEXT.getKeyValue()));

        System.setProperty(JOLOKIA_AGENT_URL, url);
        System.out.println("Jolokia: Agent started with URL " + url);

        return toReturn;
    }

    private static int getBacklog(Map<String, String> pConfig) {
        int backLog = DEFAULT_BACKLOG;
        if (pConfig.get("backlog") != null) {
            backLog = Integer.parseInt(pConfig.get("backlog"));
        }
        return backLog;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void startServer(final HttpServer pServer) {
        ThreadGroup threadGroup = new ThreadGroup("jolokia");
        threadGroup.setDaemon(false);
        // Starting server in an own thread group with a fixed name
        // so that the cleanup thread can recognize it.
        Thread starterThread = new Thread(threadGroup,new Runnable() {
            @Override
            public void run() {
                pServer.start();
            }
        });
        starterThread.start();
        cleaner = new CleanUpThread(pServer,threadGroup);
        cleaner.start();
    }

    private static void stopAgent() {
        if (cleaner != null) {
            // Instructs cleaner thread to finish and stop the server
            cleaner.stopServer();
        }
    }

    private static String getContextPath(Map<ConfigKey, String> pJolokiaConfig) {
        String context = pJolokiaConfig.get(ConfigKey.AGENT_CONTEXT);
        if (context == null) {
            context = ConfigKey.AGENT_CONTEXT.getDefaultValue();
        }
        if (!context.endsWith("/")) {
            context += "/";
        }
        return context;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static Map<String, String> parseArgs(String pAgentArgs) {
        Map<String,String> ret = new HashMap<String, String>();
        if (pAgentArgs != null && pAgentArgs.length() > 0) {
            for (String arg : pAgentArgs.split(",")) {
                String[] prop = arg.split("=");
                if (prop == null || prop.length != 2) {
                    System.err.println("jolokia: Invalid option '" + arg + "'. Ignoring");
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

    @SuppressWarnings("PMD.SystemPrintln")
    private static Map<String, String> readConfig(String pFilename) {
        File file = new File(pFilename);
        try {
            InputStream is = new FileInputStream(file);
            return readPropertiesFromInputStream(is,pFilename);
        } catch (FileNotFoundException e) {
            System.err.println("jolokia: Configuration file " + pFilename + " does not exist");
            return new HashMap<String, String>();
        }
    }

    private static Map<String, String> getDefaultConfig() {
        InputStream is =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("jolokia-agent.properties");
        return readPropertiesFromInputStream(is,"jolokia-agent.properties");
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static Map<String, String> readPropertiesFromInputStream(InputStream pIs,String pLabel) {
        Map ret = new HashMap<String, String>();
        if (pIs == null) {
            return ret;
        }
        Properties props = new Properties();
        try {
            props.load(pIs);
            ret.putAll(props);
        } catch (IOException e) {
            System.err.println("jolokia: Cannot load default properties " + pLabel + " : " + e);
        }
        return ret;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static Executor getExecutor(Map<String,String> pConfig) {
        String executor = pConfig.get("executor");
        if ("fixed".equalsIgnoreCase(executor)) {
            String nrS = pConfig.get("threadNr");
            int threads = 5;
            if (nrS != null) {
                threads = Integer.parseInt(nrS);
            }
            return Executors.newFixedThreadPool(threads);
        } else if ("cached".equalsIgnoreCase(executor)) {
            return Executors.newCachedThreadPool();
        } else {
            if (!"single".equalsIgnoreCase(executor)) {
                System.err.println("jolokia: Unknown executor '" + executor + "'. Using a single thread");
            }
            return Executors.newSingleThreadExecutor();
        }
    }


    private static Authenticator getAuthentiator(Map<ConfigKey, String> pJolokiaConfig) {
        final String user = pJolokiaConfig.get(ConfigKey.USER);
        final String password = pJolokiaConfig.get(ConfigKey.PASSWORD);
        if (user == null || password == null) {
            throw new SecurityException("No user and/or password given: user = " + user +
                    ", password = " + (password != null ? "(set)" : "null"));
        }
        return new BasicAuthenticator("jolokia") {
            @Override
            public boolean checkCredentials(String pUserGiven, String pPasswordGiven) {
                return user.equals(pUserGiven) && password.equals(pPasswordGiven);
            }
        };
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static HttpServer createHttpsServer(InetSocketAddress pSocketAddress, final Map<String,String> pConfig) {
        // initialise the HTTPS server
        try {
            HttpsServer server = HttpsServer.create(pSocketAddress, getBacklog(pConfig));
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = getKeystorePassword(pConfig);
            KeyStore ks = KeyStore.getInstance ("JKS");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream (getKeystore(pConfig));
                ks.load(fis, password);
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
            // setup the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance ("SunX509");
            kmf.init(ks, password);

            // setup the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance ("SunX509");
            tmf.init(ks);

            // setup the HTTPS context and parameters
            sslContext.init (kmf.getKeyManagers(),tmf.getTrustManagers(), null);
            server.setHttpsConfigurator (new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        // initialise the SSL context
                        SSLContext context = SSLContext.getDefault();
                        SSLEngine engine = context.createSSLEngine();
                        // TODO: Allow client authentication via configuration
                        params.setNeedClientAuth(getClientSslAuthentication(pConfig));
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        // get the default parameters
                        params.setSSLParameters(context.getDefaultSSLParameters());
                    } catch (NoSuchAlgorithmException e) {
                        System.err.println("jolokia: Exception while configuring SSL context: " + e);
                    }
                }
            });
            return server;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot use keystore for https communication: " + e,e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open keystore for https communication: " + e,e);
        }
    }

    private static boolean getClientSslAuthentication(Map<String, String> pConfig) {
        String auth = pConfig.get("useSslClientAuthentication");
        return auth != null && Boolean.getBoolean(auth);
    }

    private static String getKeystore(Map<String, String> pConfig) {
        String keystore = pConfig.get("keystore");
        if (keystore == null) {
            throw new IllegalArgumentException("No keystore defined for HTTPS protocol. " +
                                                       "Please use the 'keystore' option to point to a valid keystore");
        }
        return keystore;
    }

    private static char[] getKeystorePassword(Map<String, String> pConfig) {
       String password = pConfig.get("keystorePassword");
        return password != null ? password.toCharArray() : new char[0];
    }



}
