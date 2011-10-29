package org.jolokia.jvmagent;

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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

import com.sun.net.httpserver.*;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for creating the HttpServer used for exporting
 * the Jolokia protocol
 *
 * @author roland
 * @since 12.08.11
 */
public class JolokiaServer {


    // Overal configuration
    private ServerConfig config;

    // Thread for proper cleaning up our server thread
    // on exit
    private CleanupThread cleaner = null;

    // Http/Https server to use
    private HttpServer httpServer;

    // Agent URL
    private String url;

    // Handler for jolokia requests
    private JolokiaHttpHandler jolokiaHttpHandler;

    // the thread factory
    private ThreadFactory threadFactory = new SimpleThreadFactory();

    /**
     * Create the Jolokia server, i.e. the HttpServer for serving Jolokia requests.
     *
     * @param pConfig configuration for this server
     * @throws IOException if initialization fails
     */
    public JolokiaServer(ServerConfig pConfig) throws IOException {
        config = pConfig;

        initServer();
    }


    /**
     * Start HttpServer
     */
    public void start() {
        jolokiaHttpHandler.start();

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
            // Instructs cleaner thread to finish and stop the server
            cleaner.stopServer();
        }
    }

    /**
     * URL how this agent can be reached from the outsid.
     *
     * @return the agent URL
     */
    public String getUrl() {
        return url;
    }

    // =========================================================================================

    private void initServer() throws IOException {

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

    private class SimpleThreadFactory implements ThreadFactory {
	public Thread newThread(Runnable r) {
	    Thread t = new Thread(r);
	    t.setDaemon(true);
	    return t;
	}
    }

    private void initializeExecutor() {
        Executor executor;
        String mode = config.getExecutor();
        if ("fixed".equalsIgnoreCase(mode)) {
            executor = Executors.newFixedThreadPool(config.getThreadNr(), threadFactory);
        } else if ("cached".equalsIgnoreCase(mode)) {
            executor = Executors.newCachedThreadPool(threadFactory);
        } else {
            executor = Executors.newSingleThreadExecutor(threadFactory);
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

    // ======================================================================================
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

