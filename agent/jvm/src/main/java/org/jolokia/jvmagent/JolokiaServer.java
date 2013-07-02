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
import org.jolokia.backend.dispatcher.RequestDispatcher;
import org.jolokia.backend.dispatcher.RequestDispatcherImpl;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.restrictor.PolicyRestrictorFactory;
import org.jolokia.service.JolokiaContext;
import org.jolokia.service.JolokiaServiceManager;
import org.jolokia.service.impl.ClasspathServiceCreator;
import org.jolokia.service.impl.JolokiaServiceManagerImpl;
import org.jolokia.util.LogHandler;
import org.jolokia.util.StdoutLogHandler;

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

    // Thread for proper cleaning up our server thread
    // on exit
    private CleanupThread cleaner = null;

    // Http/Https server to use
    private HttpServer httpServer;

    // HttpServer address
    private InetSocketAddress serverAddress;

    // Agent URL
    private String url;

    // Thread factory which creates only daemon threads
    private ThreadFactory daemonThreadFactory = new DaemonThreadFactory();

    // Service Manager in use
    private JolokiaServiceManager serviceManager;

    // Whether we are using our own HTTP Server
    private boolean useOwnServer = false;

    // HttpContext created when we start it up
    private HttpContext httpContext;

    /**
     * Create the Jolokia server which in turn creates an HttpServer for serving Jolokia requests.
     *
     * @param pConfig configuration for this server
     * @throws IOException if initialization fails
     */
    public JolokiaServer(JolokiaServerConfig pConfig) throws IOException {
        init(pConfig);
    }

    /**
     * Create the Jolokia server by using an existing HttpServer to which a request handler
     * gets added.
     *
     * @param pServer HttpServer to use
     * @param pConfig configuration for this server
     */
    public JolokiaServer(HttpServer pServer,JolokiaServerConfig pConfig) {
        init(pServer, pConfig);
    }

    /**
     * No arg constructor usable by subclasses. The {@link #init(JolokiaServerConfig)} must be called later on
     * for initialization
     */
    protected JolokiaServer() {}

    /**
     * Start this server. If we manage an own HttpServer, then the HttpServer will
     * be started as well.
     */
    public void start() {
        JolokiaContext jolokiaContext = serviceManager.start();
        RequestDispatcher requestDispatcher = new RequestDispatcherImpl(jolokiaContext);
        JolokiaHttpHandler jolokiaHttpHandler = new JolokiaHttpHandler(jolokiaContext, requestDispatcher);

        httpContext = httpServer.createContext(config.getContextPath(), jolokiaHttpHandler);
        // Add authentication if configured
        final Authenticator authenticator = config.getAuthenticator();
        if (authenticator != null) {
            httpContext.setAuthenticator(authenticator);
        }

        if (useOwnServer) {
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
        httpServer.removeContext(httpContext);
        serviceManager.stop();

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
     * @throws IOException if the creation of the HttpServer fails
     */
    protected final void init(JolokiaServerConfig pConfig) throws IOException {
        // We manage it on our own
        init(createHttpServer(pConfig),pConfig);
        useOwnServer = true;
    }

    /**
     * Initialize this JolokiaServer with the given HttpServer. The calle is responsible for managing (starting/stopping)
     * the HttpServer.
     *
     * @param pServer server to use
     * @param pConfig configuration
     */
    private void init(HttpServer pServer, JolokiaServerConfig pConfig)  {
        config = pConfig;
        httpServer = pServer;

        // Create proper context along with handler

        Configuration jolokiaCfg = config.getJolokiaConfig();
        // TODO: StdouLogHandler should reference the configuration directly to determine, whether debug is switched
        // on or not.
        LogHandler log = new StdoutLogHandler(Boolean.parseBoolean(jolokiaCfg.getConfig(ConfigKey.DEBUG)));
        serviceManager = new JolokiaServiceManagerImpl(
                jolokiaCfg,log,
                PolicyRestrictorFactory.createRestrictor(jolokiaCfg.getConfig(ConfigKey.POLICY_LOCATION), log)
        );
        serviceManager.addServices(new ClasspathServiceCreator("services"));

        // Get own URL for later reference
        serverAddress = pServer.getAddress();
        url = extractUrl(pConfig);
    }

    private String extractUrl(JolokiaServerConfig pConfig) {
        InetAddress realAddress;
        int port;
        if (serverAddress != null) {
            realAddress = serverAddress.getAddress();
            port = serverAddress.getPort();
        } else {
            realAddress = pConfig.getAddress();
            port = pConfig.getPort();
        }
        return String.format("%s://%s:%d%s",
                            pConfig.getProtocol(),realAddress.getCanonicalHostName(),port, pConfig.getContextPath());
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

