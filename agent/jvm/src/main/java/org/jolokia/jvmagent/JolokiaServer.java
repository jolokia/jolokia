package org.jolokia.jvmagent;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.concurrent.*;

import javax.net.ssl.*;

import com.sun.net.httpserver.*;

/**
 * Factory for creating the HttpServer used for exporting
 * the Jolokia protocol
 *
 * @author roland
 * @since 12.08.11
 */
public class JolokiaServer {

    // Overall configuration
    private JolokiaServerConfig config;

    // Whether the initialisation should be done lazy
    private boolean lazy;

    // Thread for proper cleaning up our server thread
    // on exit
    private CleanupThread cleaner = null;

    // Http/Https server to use
    private HttpServer httpServer;

    // Agent URL
    private String url;

    // Handler for jolokia requests
    private JolokiaHttpHandler jolokiaHttpHandler;

    // Thread factory which creates only daemon threads
    private ThreadFactory daemonThreadFactory = new DaemonThreadFactory();

    /**
     * Create the Jolokia server, i.e. the HttpServer for serving Jolokia requests.
     *
     * @param pConfig configuration for this server
     * @param pLazy lazy initialisation if true. This is required for agents
     *              configured via startup options since at this early boot time
     *              the JVM is not fully setup for the server detectors to work
     * @throws IOException if initialization fails
     */
    public JolokiaServer(JolokiaServerConfig pConfig, boolean pLazy) throws IOException {
        init(pConfig, pLazy);
    }

    /**
     * No arg constructor usable by subclasses. The {@link #init(JolokiaServerConfig, boolean)} must be called later on
     * for initialization
     */
    protected JolokiaServer() {}

    /**
     * Start HttpServer
     */
    public void start() {
        jolokiaHttpHandler.start(lazy);

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

    /**
     * Stop the HTTP server
     */
    public void stop() {
        jolokiaHttpHandler.stop();

        if (cleaner != null) {
            cleaner.stopServer();
        }
    }

    /**
     * URL how this agent can be reached from the outside.
     *
     * @return the agent URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Get configuration for this server
     *
     * @return server configuration
     */
    public JolokiaServerConfig getServerConfig() {
        return config;
    }

    // =========================================================================================

    protected final void init(JolokiaServerConfig pConfig, boolean pLazy) throws IOException {
        config = pConfig;
        lazy = pLazy;

        int port = config.getPort();
        InetAddress address = config.getAddress();
        String protocol = config.getProtocol();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);

        if (protocol.equalsIgnoreCase("https")) {
            httpServer = createHttpsServer(socketAddress);
        } else {
            httpServer = HttpServer.create(socketAddress,config.getBacklog());
        }


        // Create proper context along with handler
        final String contextPath = config.getContextPath();
        jolokiaHttpHandler = new JolokiaHttpHandler(config.getJolokiaConfig());
        HttpContext context = httpServer.createContext(contextPath, jolokiaHttpHandler);

        // Special customizations
        addAuthenticatorIfNeeded(config.getUser(),config.getPassword(),context);
        initializeExecutor();

        InetSocketAddress realSocketAddress = httpServer.getAddress();
        InetAddress realAddress = realSocketAddress.getAddress() != null ? realSocketAddress.getAddress() : address;
        url = String.format("%s://%s:%d%s",
                            protocol,realAddress.getCanonicalHostName(),realSocketAddress.getPort(),contextPath);
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
        Executor executor;
        String mode = config.getExecutor();
        if ("fixed".equalsIgnoreCase(mode)) {
            executor = Executors.newFixedThreadPool(config.getThreadNr(), daemonThreadFactory);
        } else if ("cached".equalsIgnoreCase(mode)) {
            executor = Executors.newCachedThreadPool(daemonThreadFactory);
        } else {
            executor = Executors.newSingleThreadExecutor(daemonThreadFactory);
        }
        httpServer.setExecutor(executor);
    }


    // =========================================================================================================
    // HTTPS handling

    private HttpServer createHttpsServer(InetSocketAddress pSocketAddress) {
        // initialise the HTTPS server
        try {
            HttpsServer server = HttpsServer.create(pSocketAddress, config.getBacklog());
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = config.getKeystorePassword();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(config.getKeystore());
                ks.load(fis, password);
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
            // setup the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            // setup the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // setup the HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(),tmf.getTrustManagers(), null);
            server.setHttpsConfigurator(new JolokiaHttpsConfigurator(sslContext,config.useClientAuthentication()));
            return server;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot use keystore for https communication: " + e,e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open keystore for https communication: " + e,e);
        }
    }

    /**
     * @return the address that the server is listening on. Thus, a program can initialize the server
     * with 'port 0' and then retrieve the actual running port that was bound.
     */
    public InetSocketAddress getAddress() {
        return httpServer.getAddress();
    }

    // ======================================================================================

    // Thread factory for creating daemon threads only
    private static class DaemonThreadFactory implements ThreadFactory {

        @Override
        /** {@inheritDoc} */
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    }

    // Simple authenticator
    private static class JolokiaAuthenticator extends BasicAuthenticator {
        private String user;
        private String password;

        /**
         * Authenticator which checks agains a given user and password
         *
         * @param pUser user to check again
         * @param pPassword her password
         */
        JolokiaAuthenticator(String pUser, String pPassword) {
            super("jolokia");
            user = pUser;
            password = pPassword;
        }

        /** {@inheritDoc} */
        public boolean checkCredentials(String pUserGiven, String pPasswordGiven) {
            return user.equals(pUserGiven) && password.equals(pPasswordGiven);
        }
    }

    // HTTPS configurator
    private static final class JolokiaHttpsConfigurator extends HttpsConfigurator {
        private boolean useClientAuthentication;

        private JolokiaHttpsConfigurator(SSLContext pSSLContext,boolean pUseClientAuthenication) {
            super(pSSLContext);
            useClientAuthentication = pUseClientAuthenication;
        }

        /** {@inheritDoc} */
        public void configure(HttpsParameters params) {
            try {
                // initialise the SSL context
                SSLContext context = SSLContext.getDefault();
                SSLEngine engine = context.createSSLEngine();
                params.setNeedClientAuth(useClientAuthentication);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                params.setProtocols(engine.getEnabledProtocols());

                // get the default parameters
                params.setSSLParameters(context.getDefaultSSLParameters());
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("jolokia: Exception while configuring SSL context: " + e,e);
            }
        }
    }
}

