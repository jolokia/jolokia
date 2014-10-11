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
import java.net.*;
import java.security.*;
import java.util.concurrent.*;

import javax.net.ssl.*;

import com.sun.net.httpserver.*;
import com.sun.net.httpserver.Authenticator;
import org.jolokia.config.ConfigKey;
import org.jolokia.util.NetworkUtil;

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

    // HttpServer address
    private InetSocketAddress serverAddress;

    // Agent URL
    private String url;

    // Handler for jolokia requests
    private JolokiaHttpHandler jolokiaHttpHandler;

    // Thread factory which creates only daemon threads
    private ThreadFactory daemonThreadFactory = new DaemonThreadFactory();

    /**
     * Create the Jolokia server which in turn creates an HttpServer for serving Jolokia requests.
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
     * Create the Jolokia server by using an existing HttpServer to which a request handler
     * gets added.
     *
     * @param pServer HttpServer to use
     * @param pConfig configuration for this server
     * @param pLazy lazy initialisation if true. This is required for agents
     *              configured via startup options since at this early boot time
     *              the JVM is not fully setup for the server detectors to work
     */
    public JolokiaServer(HttpServer pServer,JolokiaServerConfig pConfig, boolean pLazy) {
        init(pServer,pConfig,pLazy);
    }

    /**
     * No arg constructor usable by subclasses. The {@link #init(JolokiaServerConfig, boolean)} must be called later on
     * for initialization
     */
    protected JolokiaServer() {}

    /**
     * Start this server. If we manage an own HttpServer, then the HttpServer will
     * be started as well.
     */
    public void start() {
        // URL as configured takes precedence
        String configUrl = NetworkUtil.replaceExpression(config.getJolokiaConfig().get(ConfigKey.DISCOVERY_AGENT_URL));
        jolokiaHttpHandler.start(lazy,configUrl != null ? configUrl : url, config.getAuthenticator() != null);

        if (httpServer != null) {
            // Starting our own server in an own thread group with a fixed name
            // so that the cleanup thread can recognize it.
            ThreadGroup threadGroup = new ThreadGroup("jolokia");
            threadGroup.setDaemon(false);

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

    /**
     * Initialize this JolokiaServer and use an own created HttpServer
     *
     * @param pConfig configuartion to use
     * @param pLazy whether to do the inialization lazy or not
     * @throws IOException if the creation of the HttpServer fails
     */
    protected final void init(JolokiaServerConfig pConfig, boolean pLazy) throws IOException {
        // We manage it on our own
        httpServer = createHttpServer(pConfig);
        init(httpServer,pConfig,pLazy);
    }

    /**
     * Initialize this JolokiaServer with the given HttpServer. The calle is responsible for managing (starting/stopping)
     * the HttpServer.
     *
     * @param pServer server to use
     * @param pConfig configuration
     * @param pLazy whether the initialization should be done lazy or not
     */
    protected final void init(HttpServer pServer, JolokiaServerConfig pConfig, boolean pLazy)  {
        config = pConfig;
        lazy = pLazy;

        // Create proper context along with handler
        final String contextPath = pConfig.getContextPath();
        jolokiaHttpHandler = new JolokiaHttpHandler(pConfig.getJolokiaConfig());
        HttpContext context = pServer.createContext(contextPath, jolokiaHttpHandler);

        // Add authentication if configured
        final Authenticator authenticator = pConfig.getAuthenticator();
        if (authenticator != null) {
            context.setAuthenticator(authenticator);
        }

        url = detectAgentUrl(pServer, pConfig, contextPath);
    }

    private String detectAgentUrl(HttpServer pServer, JolokiaServerConfig pConfig, String pContextPath) {
        serverAddress= pServer.getAddress();
        InetAddress realAddress;
        int port;
        if (serverAddress != null) {
            realAddress = serverAddress.getAddress();
            if (realAddress.isAnyLocalAddress()) {
                try {
                    realAddress = NetworkUtil.getLocalAddress();
                } catch (IOException e) {
                    try {
                        realAddress = InetAddress.getLocalHost();
                    } catch (UnknownHostException e1) {
                        // Ok, ok. We take the orginal one
                        realAddress = serverAddress.getAddress();
                    }
                }
            }
            port = serverAddress.getPort();
        } else {
            realAddress = pConfig.getAddress();
            port = pConfig.getPort();
        }

        return String.format("%s://%s:%d%s",
                             pConfig.getProtocol(),realAddress.getHostAddress(),port, pContextPath);
    }

    /**
     * Create the HttpServer to use. Can be overridden if a custom or already existing HttpServer should be
     * used
     *
     * @return HttpServer to use
     * @throws IOException if something fails during the initialisation
     */
    private HttpServer createHttpServer(JolokiaServerConfig pConfig) throws IOException {
        int port = pConfig.getPort();
        InetAddress address = pConfig.getAddress();
        String protocol = pConfig.getProtocol();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);

        HttpServer server =
                protocol.equalsIgnoreCase("https") ?
                        createHttpsServer(socketAddress, pConfig) :
                        HttpServer.create(socketAddress, pConfig.getBacklog());

        // Prepare executor pool
        Executor executor;
        String mode = pConfig.getExecutor();
        if ("fixed".equalsIgnoreCase(mode)) {
            executor = Executors.newFixedThreadPool(pConfig.getThreadNr(), daemonThreadFactory);
        } else if ("cached".equalsIgnoreCase(mode)) {
            executor = Executors.newCachedThreadPool(daemonThreadFactory);
        } else {
            executor = Executors.newSingleThreadExecutor(daemonThreadFactory);
        }
        server.setExecutor(executor);

        return server;
    }


    // =========================================================================================================
    // HTTPS handling

    private HttpServer createHttpsServer(InetSocketAddress pSocketAddress,JolokiaServerConfig pConfig) {
        // initialise the HTTPS server
        try {
            HttpsServer server = HttpsServer.create(pSocketAddress, pConfig.getBacklog());
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = pConfig.getKeystorePassword();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(pConfig.getKeystore());
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
            server.setHttpsConfigurator(new JolokiaHttpsConfigurator(sslContext, pConfig.useSslClientAuthentication()));
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
        return serverAddress;
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

    // HTTPS configurator
    private static final class JolokiaHttpsConfigurator extends HttpsConfigurator {
        private boolean useClientAuthentication;

        private JolokiaHttpsConfigurator(SSLContext pSSLContext,boolean pUseClientAuthentication) {
            super(pSSLContext);
            useClientAuthentication = pUseClientAuthentication;
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
                SSLParameters defaultSSLParameters = context.getDefaultSSLParameters();
                defaultSSLParameters.setNeedClientAuth(useClientAuthentication);
                params.setSSLParameters(defaultSSLParameters);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("jolokia: Exception while configuring SSL context: " + e,e);
            }
        }
    }
}

