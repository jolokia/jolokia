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

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.*;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.restrictor.PolicyRestrictorFactory;
import org.jolokia.server.core.service.*;
import org.jolokia.server.core.service.impl.ClasspathServiceCreator;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.NetworkUtil;

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
     * Create the Jolokia server which in turn creates an HttpServer for serving Jolokia requests. This
     * uses a loghandler which prints out to stdout.
     *
     * @param pConfig configuration for this server
     * @throws IOException if initialization fails
     */
    public JolokiaServer(JolokiaServerConfig pConfig) throws IOException {
        init(pConfig, null);
    }

    /**
     * Create the Jolokia server which in turn creates an HttpServer for serving Jolokia requests.
     *
     * @param pConfig configuration for this server
     * @param pLogHandler log handler to use or <code>null</code> if logging should go to stdout
     * @throws IOException if initialization fails
     */
    public JolokiaServer(JolokiaServerConfig pConfig, LogHandler pLogHandler) throws IOException {
        init(pConfig, pLogHandler);
    }

    /**
     * Create the Jolokia server by using an existing HttpServer to which a request handler
     * gets added.
     *
     * @param pServer HttpServer to use
     * @param pConfig configuration for this server
     * @param pLogHandler log handler to use
     */
    public JolokiaServer(HttpServer pServer,JolokiaServerConfig pConfig, LogHandler pLogHandler) {
        init(pServer, pConfig, pLogHandler);
    }

    /**
     * No arg constructor usable by subclasses. The {@link #init(JolokiaServerConfig,LogHandler)} must be called later on
     * for initialization
     */
    protected JolokiaServer() {}

    /**
     * Start this server. If we manage an own HttpServer, then the HttpServer will
     * be started as well.
     */
    public void start() {
        start(false);
    }

    /**
     * Start this server. If we manage an own HttpServer, then the HttpServer will
     * be started as well.
     *
     * @param pLazy if set to true Jolokia is initialized on the first request which allows (hopefully) the rest to initialize
     *              properly
     */
    public void start(boolean pLazy) {

        HttpHandler jolokiaHttpHandler = createJolokiaHttpHandler(pLazy);
        httpContext = httpServer.createContext(config.getContextPath(), jolokiaHttpHandler);

        setupAuthentication();
        if (useOwnServer) {
            startCleanupThread();
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

    /**
     * @return the address that the server is listening on. Thus, a program can initialize the server
     * with 'port 0' and then retrieve the actual running port that was bound.
     */
    public InetSocketAddress getAddress() {
        return serverAddress;
    }

    // =========================================================================================

    /**
     * Initialize this JolokiaServer and use an own created HttpServer
     *
     * @param pConfig configuartion to use
     * @param pLogHandler log handler to use
     * @throws IOException if the creation of the HttpServer fails
     */
    protected final void init(JolokiaServerConfig pConfig,LogHandler pLogHandler) throws IOException {
        // We manage it on our own
        init(createHttpServer(pConfig),pConfig,pLogHandler);
        useOwnServer = true;
    }

    /**
     * Allow to add service from within a sub class. This method should be called before
     * this server is started vie {@link #start(boolean)}
     *
     * @param pService service to add
     */
    protected void addService(JolokiaService pService) {
        serviceManager.addService(pService);
    }

    /**
     * Initialize this JolokiaServer with the given HttpServer. The calle is responsible for managing (starting/stopping)
     * the HttpServer.
     *
     * @param pServer server to use
     * @param pConfig configuration
     * @param pLogHandler log handler to use.
     */
    private void init(HttpServer pServer, JolokiaServerConfig pConfig, LogHandler pLogHandler)  {
        config = pConfig;
        httpServer = pServer;

        // Create proper context along with handler

        Configuration jolokiaCfg = config.getJolokiaConfig();
        LogHandler log = pLogHandler != null ?
                pLogHandler :
                createLogHandler(jolokiaCfg.getConfig(ConfigKey.LOGHANDLER_CLASS),
                                 Boolean.parseBoolean(jolokiaCfg.getConfig(ConfigKey.DEBUG)));

        serviceManager =
                JolokiaServiceManagerFactory.createJolokiaServiceManager(
                        jolokiaCfg,
                        log,
                        PolicyRestrictorFactory.createRestrictor(jolokiaCfg.getConfig(ConfigKey.POLICY_LOCATION), log));
        serviceManager.addServices(new ClasspathServiceCreator("services"));

        // Get own URL for later reference
        serverAddress = pServer.getAddress();
        url = detectAgentUrl(pServer, pConfig, pConfig.getContextPath());
    }

    // Create the JolokiaHttpHandler either directly or lazily
    private HttpHandler createJolokiaHttpHandler(boolean pLazy) {
        if (pLazy) {
            return new LazyInitializedJolokiaHttpHandler();
        } else {
            return startupJolokiaContext();
        }
    }

    // Startup the context and create the HttpHandler
    private HttpHandler startupJolokiaContext() {
        JolokiaContext jolokiaContext = serviceManager.start();
        JolokiaHttpHandler jolokiaHttpHandler = new JolokiaHttpHandler(jolokiaContext);
        updateAgentUrl(jolokiaContext);
        return jolokiaHttpHandler;
    }

    // Update the Agent URL from the configuration or own URL
    private void updateAgentUrl(JolokiaContext pJolokiaContext) {
        // URL as configured takes precedence
        String configUrl = config.getJolokiaConfig().getConfig(ConfigKey.DISCOVERY_AGENT_URL);
        pJolokiaContext.getAgentDetails().updateAgentParameters(configUrl != null ? configUrl : url,
                                                               config.getAuthenticator() != null);
    }

    // Prepare the authentication
    private void setupAuthentication() {
        // Add authentication if configured
        final Authenticator authenticator = config.getAuthenticator();
        if (authenticator != null) {
            httpContext.setAuthenticator(authenticator);
        }
    }


    // If running an own server, we need to check that shutdown properly
    private void startCleanupThread() {
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

    // Creat a log handler from either the given class or by creating a default log handler printing
    // out to stderr
    private LogHandler createLogHandler(String pLogHandlerClass, final boolean pIsDebug) {
        if (pLogHandlerClass != null) {
            return ClassUtil.newInstance(pLogHandlerClass);
        } else {
            return new LogHandler.StdoutLogHandler(pIsDebug);
        }
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

    // ====================================================================================================

    // A handler class which does the initialization lazily on the first request
    // Useful for server detection since the app container is not initialized from the very beginning
    private class LazyInitializedJolokiaHttpHandler implements HttpHandler {

        // Initialize used for late initialization
        // ("volatile: because we use double-checked locking later on
        // --> http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)
        private volatile HttpHandler realHandler;

        @Override
        public void handle(HttpExchange pHttpExchange) throws IOException {
            if (realHandler == null) {
                synchronized (this) {
                    if (realHandler == null) {
                        realHandler = startupJolokiaContext();
                    }
                }
            }
            realHandler.handle(pHttpExchange);
        }
    }

}

