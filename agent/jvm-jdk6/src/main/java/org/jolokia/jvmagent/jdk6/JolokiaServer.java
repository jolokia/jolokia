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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.security.*;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

import com.sun.net.httpserver.*;
import org.jolokia.util.ConfigKey;

/**
 * Factory for creating the HttpServer used for exporting
 * the Jolokia protocol
 *
 * @author roland
 * @since 12.08.11
 */
@SuppressWarnings("PMD.SystemPrintln" )
public class JolokiaServer {

    public static final int DEFAULT_PORT = 8778;
    public static final int DEFAULT_BACKLOG = 10;
    public static final String DEFAULT_PROTOCOL = "http";

    // Jolokia configuration is used for general jolokia config, the untyped configuration
    // is used for this agent only
    private Map<String,String> agentConfig;
    private Map<ConfigKey,String> jolokiaConfig;

    private CleanupThread cleaner = null;

    private HttpServer httpServer;

    private String url;

    public JolokiaServer(Map<String, String> pConfig) throws IOException {
        this.agentConfig = pConfig;
        jolokiaConfig = ConfigKey.extractConfig(agentConfig);

        initServer();
    }


    public void start() {
        ThreadGroup threadGroup = new ThreadGroup("jolokia");
        threadGroup.setDaemon(false);
        // Starting server in an own thread group with a fixed name
        // so that the cleanup thread can recognize it.
        Thread starterThread = new Thread(threadGroup,new Runnable() {
            @Override
            public void run() {
                httpServer.start();
            }
        });
        starterThread.start();
        cleaner = new CleanupThread(httpServer,threadGroup);
        cleaner.start();
    }

    public void stop() {
        if (cleaner != null) {
            // Instructs cleaner thread to finish and stop the server
            cleaner.stopServer();
        }
    }

    public String getUrl() {
        return url;
    }

    // =========================================================================================

    private void initServer() throws IOException {

        int port = getPort();
        InetAddress address = getLocalAddress();
        String protocol = getProtocol();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);

        if (protocol.equalsIgnoreCase("https")) {
            httpServer = createHttpsServer(socketAddress);
        } else {
            httpServer = HttpServer.create(socketAddress,getBacklog());
        }


        // Create proper context along with handler
        final String contextPath = getContextPath();
        HttpContext context = httpServer.createContext(contextPath, new JolokiaHttpHandler(jolokiaConfig));

        // Special customizations
        addAuthenticatorIfNeeded(jolokiaConfig.get(ConfigKey.USER),
                                 jolokiaConfig.get(ConfigKey.PASSWORD),
                                 context);
        initializeExecutor();

        url = String.format("%s://%s:%d%s",protocol,address.getCanonicalHostName(),port,contextPath);
    }

    private void addAuthenticatorIfNeeded(final String user, final String password, HttpContext pContext) {
        if (user != null) {
            if (password == null) {
                throw new SecurityException("No password given for user " + user);
            }
            pContext.setAuthenticator(new JolokiaAuthenticator(user,password));
        }
    }

    private void initializeExecutor() {
        Executor result;
        String executor = agentConfig.get("executor");
        if ("fixed".equalsIgnoreCase(executor)) {
            String nrS = agentConfig.get("threadNr");
            int threads = 5;
            if (nrS != null) {
                threads = Integer.parseInt(nrS);
            }
            result = Executors.newFixedThreadPool(threads);
        } else if ("cached".equalsIgnoreCase(executor)) {
            result = Executors.newCachedThreadPool();
        } else {
            if (!"single".equalsIgnoreCase(executor)) {
                System.err.println("jolokia: Unknown executor '" + executor + "'. Using a single thread");
            }
            result = Executors.newSingleThreadExecutor();
        }
        httpServer.setExecutor(result);
    }

    private String getProtocol() {
        if ("https".equals(agentConfig.get("protocol"))) {
            return "https";
        } else {
            return DEFAULT_PROTOCOL;
        }
    }

    private InetAddress getLocalAddress() throws UnknownHostException {
        if (agentConfig.get("host") != null) {
            return InetAddress.getByName(agentConfig.get("host"));
        } else {
            return InetAddress.getLocalHost();
        }
    }

    private int getPort() {
        if (agentConfig.get("port") != null) {
            return Integer.parseInt(agentConfig.get("port"));
        } else {
            return DEFAULT_PORT;
        }
    }

    private int getBacklog() {
        if (agentConfig.get("backlog") != null) {
            return Integer.parseInt(agentConfig.get("backlog"));
        } else {
            return DEFAULT_BACKLOG;
        }
    }

    private String getContextPath() {
        String context = jolokiaConfig.get(ConfigKey.AGENT_CONTEXT);
        if (context == null) {
            context = ConfigKey.AGENT_CONTEXT.getDefaultValue();
        }
        if (!context.endsWith("/")) {
            context += "/";
        }
        return context;
    }

    private boolean useClientAuthentication() {
        String auth = agentConfig.get("useSslClientAuthentication");
        return auth != null && Boolean.getBoolean(auth);
    }


    // =========================================================================================================
    // HTTPS handling

    private HttpServer createHttpsServer(InetSocketAddress pSocketAddress) {
        // initialise the HTTPS server
        try {
            HttpsServer server = HttpsServer.create(pSocketAddress, getBacklog());
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = getKeystorePassword(agentConfig);
            KeyStore ks = KeyStore.getInstance ("JKS");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream (getKeystore(agentConfig));
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
            sslContext.init(kmf.getKeyManagers(),tmf.getTrustManagers(), null);
            server.setHttpsConfigurator(new JolokiaHttpsConfigurator(sslContext,useClientAuthentication()));
            return server;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot use keystore for https communication: " + e,e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open keystore for https communication: " + e,e);
        }
    }

    private String getKeystore(Map<String, String> pConfig) {
        String keystore = pConfig.get("keystore");
        if (keystore == null) {
            throw new IllegalArgumentException("No keystore defined for HTTPS protocol. " +
                                                       "Please use the 'keystore' option to point to a valid keystore");
        }
        return keystore;
    }

    private  char[] getKeystorePassword(Map<String, String> pConfig) {
        String password = pConfig.get("keystorePassword");
        return password != null ? password.toCharArray() : new char[0];
    }

    // ======================================================================================
    // Simple authenticator
    private static class JolokiaAuthenticator extends BasicAuthenticator {
        private String user;
        private String password;

        JolokiaAuthenticator(String pUser, String pPassword) {
            super("jolokia");
            user = pUser;
            password = pPassword;
        }

        public boolean checkCredentials(String pUserGiven, String pPasswordGiven) {
            return user.equals(pUserGiven) && password.equals(pPasswordGiven);
        }
    }

    // HTTPS configurator
    private static class JolokiaHttpsConfigurator extends HttpsConfigurator {
        private boolean useClientAuthentication;

        private JolokiaHttpsConfigurator(SSLContext pSSLContext,boolean pUseClientAuthenication) {
            super(pSSLContext);
            useClientAuthentication = pUseClientAuthenication;
        }

        public void configure(HttpsParameters params) {
            try {
                // initialise the SSL context
                SSLContext context = SSLContext.getDefault();
                SSLEngine engine = context.createSSLEngine();
                // TODO: Allow client authentication via configuration
                params.setNeedClientAuth(useClientAuthentication);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                params.setProtocols(engine.getEnabledProtocols());

                        // get the default parameters
                params.setSSLParameters(context.getDefaultSSLParameters());
            } catch (NoSuchAlgorithmException e) {
                System.err.println("jolokia: Exception while configuring SSL context: " + e);
            }
        }
    }
}

