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

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.*;

import javax.net.ssl.*;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.*;
import org.jolokia.jvmagent.handler.JolokiaHttpHandler;
import org.jolokia.jvmagent.security.KeyStoreUtil;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.restrictor.RestrictorFactory;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.impl.ClasspathServiceCreator;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.server.core.util.*;

/**
 * Factory for creating the HttpServer used for exporting
 * the Jolokia protocol
 *
 * @author roland
 * @author nevenr
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
        init(pConfig, null, null);
    }

    /**
     * Create the Jolokia server which in turn creates an HttpServer for serving Jolokia requests. This
     * uses a loghandler which prints out to stdout and allows to pass existing {@link ServerDetectorLookup}
     *
     * @param pConfig configuration for this server
     * @param pLookup existing server detector lookup to use
     * @throws IOException if initialization fails
     */
    public JolokiaServer(JolokiaServerConfig pConfig, ServerDetectorLookup pLookup) throws IOException {
        init(pConfig, null, pLookup);
    }

    /**
     * Create the Jolokia server which in turn creates an HttpServer for serving Jolokia requests.
     *
     * @param pConfig configuration for this server
     * @param pLogHandler log handler to use or <code>null</code> if logging should go to stdout
     * @throws IOException if initialization fails
     */
    public JolokiaServer(JolokiaServerConfig pConfig, LogHandler pLogHandler) throws IOException {
        init(pConfig, pLogHandler, null);
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
        init(pServer, pConfig, pLogHandler, null);
    }

    /**
     * No arg constructor usable by subclasses. The {@link #init(JolokiaServerConfig,LogHandler)} must be called later on
     * for initialization
     */
    @SuppressWarnings("unused")
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
            Runtime.getRuntime().removeShutdownHook(cleaner);
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
    @SuppressWarnings("unused")
    public JolokiaServerConfig getServerConfig() {
        return config;
    }

    /**
     * @return the address that the server is listening on. Thus, a program can initialize the server
     * with 'port 0' and then retrieve the actual running port that was bound.
     */
    @SuppressWarnings("unused")
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
        init(createHttpServer(pConfig), pConfig, pLogHandler, null);
        useOwnServer = true;
    }

    /**
     * Initialize this JolokiaServer and use an own created HttpServer. Existing {@link ServerDetectorLookup}
     * can be passed
     *
     * @param pConfig configuartion to use
     * @param pLogHandler log handler to use
     * @param pLookup existing server detector lookup to use
     * @throws IOException if the creation of the HttpServer fails
     */
    protected final void init(JolokiaServerConfig pConfig,LogHandler pLogHandler, ServerDetectorLookup pLookup) throws IOException {
        // We manage it on our own
        init(createHttpServer(pConfig), pConfig, pLogHandler, pLookup);
        useOwnServer = true;
    }

    /**
     * Allow to add service from within a subclass. This method should be called before
     * this server is started vie {@link #start(boolean)}
     *
     * @param pService service to add
     */
    @SuppressWarnings("unused")
    protected void addService(JolokiaService<?> pService) {
        serviceManager.addService(pService);
    }

    /**
     * Initialize this JolokiaServer with the given HttpServer. The calle is responsible for managing (starting/stopping)
     * the HttpServer.
     *
     * @param pServer server to use
     * @param pConfig configuration
     * @param pLogHandler log handler to use.
     * @param pLookup preconfigured {@link ServerDetectorLookup} to use
     */
    private void init(HttpServer pServer, JolokiaServerConfig pConfig, LogHandler pLogHandler, ServerDetectorLookup pLookup)  {
        config = pConfig;
        httpServer = pServer;

        Configuration jolokiaCfg = config.getJolokiaConfig();
        LogHandler log = pLogHandler != null ?
                pLogHandler :
                createLogHandler(jolokiaCfg.getConfig(ConfigKey.LOGHANDLER_CLASS),
                                 jolokiaCfg.getConfig(ConfigKey.LOGHANDLER_NAME),
                                 Boolean.parseBoolean(jolokiaCfg.getConfig(ConfigKey.DEBUG)));

        serviceManager =
                JolokiaServiceManagerFactory.createJolokiaServiceManager(
                        jolokiaCfg,
                        log,
                        RestrictorFactory.createRestrictor(jolokiaCfg, log),
                        pLookup);

        // loader used to load services - may be configured by one of the detectors
        ClassLoader loader = config.getClassLoader();
        if (loader == null) {
            loader = LocalServiceFactory.class.getClassLoader();
        }
        serviceManager.addServices(new ClasspathServiceCreator(loader, "services"));

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
        String configUrl = NetworkUtil.replaceExpression(
                config.getJolokiaConfig().getConfig(ConfigKey.DISCOVERY_AGENT_URL));
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

        Thread starterThread = new Thread(threadGroup, () -> httpServer.start());
        starterThread.start();
        cleaner = new CleanupThread(httpServer,threadGroup);
        Runtime.getRuntime().addShutdownHook(cleaner);
    }

    // Creat a log handler from either the given class or by creating a default log handler printing
    // out to stderr
    private LogHandler createLogHandler(String pLogHandlerClass, String pLogHandlerName, final boolean pIsDebug) {
        if (pLogHandlerClass != null) {
            return ClassUtil.newLogHandlerInstance(pLogHandlerClass, pLogHandlerName, pIsDebug);
        } else {
            return new StdoutLogHandler(pIsDebug);
        }
    }

    private String detectAgentUrl(HttpServer pServer, JolokiaServerConfig pConfig, String pContextPath) {
        serverAddress= pServer.getAddress();
        InetAddress realAddress;
        int port;
        if (serverAddress != null) {
            realAddress = serverAddress.getAddress();
            port = serverAddress.getPort();
        } else {
            realAddress = pConfig.getAddress();
            port = pConfig.getPort();
        }
        if (realAddress.isAnyLocalAddress()) {
            // we can't just say that Agent's URL is http://0.0.0.0:8778/jolokia for example...
            try {
                // let's be explicit here - for backward compatibilty we'll announce Agent URL using IPv4 by default
                boolean preferIPv6Addresses = Boolean.getBoolean("java.net.preferIPv6Addresses");
                if (realAddress instanceof Inet6Address && preferIPv6Addresses) {
                    realAddress = NetworkUtil.getLocalAddress(Inet6Address.class);
                } else {
                    realAddress = NetworkUtil.getLocalAddress(Inet4Address.class);
                }
            } catch (IOException e) {
                // we have to expect the unexpected
                try {
                    realAddress = InetAddress.getLocalHost();
                } catch (UnknownHostException e1) {
                    // Ok, ok. We take the original one
                    realAddress = serverAddress.getAddress();
                }
            }
        }
        String hostAddress = realAddress.getHostAddress();
        if (realAddress instanceof Inet6Address) {
            if (((Inet6Address) realAddress).getScopedInterface() != null
                || ((Inet6Address) realAddress).getScopeId() > 0) {
                int percent = hostAddress.indexOf('%');
                if (percent != -1) {
                    hostAddress = hostAddress.substring(0, percent);
                }
            }
            return String.format("%s://[%s]:%d%s",
                                 pConfig.getProtocol(), hostAddress, port, pContextPath);
        } else {
            return String.format("%s://%s:%d%s",
                                 pConfig.getProtocol(), hostAddress, port, pContextPath);
        }
    }

    /**
     * Create the HttpServer to use. Can be overridden if a custom or already existing HttpServer should be
     * used
     *
     * @return HttpServer to use
     * @throws IOException if something fails during the initialisation
     */
    private HttpServer createHttpServer(JolokiaServerConfig pConfig) throws IOException {
        // only now init the authenticator, because it may refer to a class not accessible
        // from boot/app classloader
        pConfig.initAuthenticator();

        int port = pConfig.getPort();
        InetAddress address = pConfig.getAddress();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);

        HttpServer server = pConfig.useHttps() ?
                        createHttpsServer(socketAddress, pConfig) :
                        HttpServer.create(socketAddress, pConfig.getBacklog());

        // Thread factory which creates only daemon threads
        ThreadFactory daemonThreadFactory = new DaemonThreadFactory(pConfig.getThreadNamePrefix());
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
    private HttpServer createHttpsServer(InetSocketAddress pSocketAddress, JolokiaServerConfig pConfig) {
        // initialise the HTTPS server
        try {
            HttpsServer server = HttpsServer.create(pSocketAddress, pConfig.getBacklog());
            SSLContext sslContext = SSLContext.getInstance(pConfig.getSecureSocketProtocol());

            // initialise the keystore
            KeyStore ks = getKeyStore(pConfig);

            // set up the key manager factory
            KeyManagerFactory kmf = getKeyManagerFactory(pConfig);
            kmf.init(ks, pConfig.getKeystorePassword());

            // set up the trust manager factory
            TrustManagerFactory tmf = getTrustManagerFactory(pConfig);
            tmf.init(ks);

            // set up the HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            // Update the config to filter out bad protocols or ciphers
            pConfig.updateHTTPSSettingsFromContext(sslContext);

            server.setHttpsConfigurator(new JolokiaHttpsConfigurator(sslContext, pConfig));
            return server;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot use keystore for https communication: " + e,e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open keystore for https communication: " + e,e);
        }
    }

    private TrustManagerFactory getTrustManagerFactory(JolokiaServerConfig pConfig) throws NoSuchAlgorithmException {
        String algo = pConfig.getTrustManagerAlgorithm();
        return TrustManagerFactory.getInstance(algo != null ? algo : TrustManagerFactory.getDefaultAlgorithm());
    }

    private KeyManagerFactory getKeyManagerFactory(JolokiaServerConfig pConfig) throws NoSuchAlgorithmException {
        String algo = pConfig.getKeyManagerAlgorithm();
        return KeyManagerFactory.getInstance(algo != null ? algo : KeyManagerFactory.getDefaultAlgorithm());
    }

    private KeyStore getKeyStore(JolokiaServerConfig pConfig) throws KeyStoreException, IOException,
                                                                     NoSuchAlgorithmException, CertificateException,
                                                                     InvalidKeySpecException, InvalidKeyException,
                                                                     NoSuchProviderException, SignatureException {
        char[] password = pConfig.getKeystorePassword();
        String keystoreFile = pConfig.getKeystore();
        KeyStore keystore = KeyStore.getInstance(pConfig.getKeyStoreType());
        if (keystoreFile != null) {
            // Load everything from a keystore which must include CA (if useClientSslAuthentication is used) and
            // server cert/key
            loadKeyStoreFromFile(keystore, keystoreFile, password);
        } else {
            // Load keys from PEM files
            keystore.load(null);
            updateKeyStoreFromPEM(keystore,pConfig);

            // If no server cert is configured, then use a self-signed server certificate
            if (pConfig.getServerCert() == null) {
                KeyStoreUtil.updateWithSelfSignedServerCertificate(keystore);
            }
        }
        return keystore;
    }

    private void updateKeyStoreFromPEM(KeyStore keystore, JolokiaServerConfig pConfig)
            throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException {

        if (pConfig.getCaCert() != null) {
            File caCert = getAndValidateFile(pConfig.getCaCert(),"CA cert");
            KeyStoreUtil.updateWithCaPem(keystore, caCert);
        } else if (pConfig.useSslClientAuthentication()) {
            throw new IllegalArgumentException("Cannot use client cert authentication if no CA is given with 'caCert'");
        }

        if (pConfig.getServerCert() != null) {
            // Use the provided server key
            File serverCert = getAndValidateFile(pConfig.getServerCert(),"server cert");
            if (pConfig.getServerKey() == null) {
                throw new IllegalArgumentException("Cannot use server cert from " + pConfig.getServerCert() +
                                                   " without a provided a key given with 'serverKey'");
            }
            File serverKey = getAndValidateFile(pConfig.getServerKey(),"server key");
            KeyStoreUtil.updateWithServerPems(keystore, serverCert, serverKey,
                                              pConfig.getServerKeyAlgorithm(), pConfig.getKeystorePassword());
        }
    }

    private File getAndValidateFile(String pFile, String pWhat) throws IOException {
        File ret = new File(pFile);
        if (!ret.exists()) {
            throw new FileNotFoundException("No such " + pWhat + " " + pFile);
        }
        if (!ret.canRead()) {
            throw new IOException(pWhat.substring(0,1).toUpperCase() + pWhat.substring(1) + " " + pFile + " is not readable");
        }
        return ret;
    }

    private void loadKeyStoreFromFile(KeyStore pKeyStore, String pFile, char[] pPassword)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        try (FileInputStream fis = new FileInputStream(getAndValidateFile(pFile, "keystore"))) {
            pKeyStore.load(fis, pPassword);
        }
    }

    // A handler class which does the initialization lazily on the first request
    // Useful for server detection since the app container is not initialized from the very beginning
    private class LazyInitializedJolokiaHttpHandler implements HttpHandler {

        // Initialize used for late initialization
        // ("volatile": because we use double-checked locking later on
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

