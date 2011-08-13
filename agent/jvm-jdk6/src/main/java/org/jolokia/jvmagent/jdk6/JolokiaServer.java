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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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


    // Overal configuration
    private ServerConfig config;

    // Thread for proper cleaning up our server thread
    // on exit
    private CleanupThread cleaner = null;

    // Http/Https server to use
    private HttpServer httpServer;

    // Agent URL
    private String url;

    public JolokiaServer(ServerConfig pConfig) throws IOException {
        config = pConfig;

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
        HttpContext context = httpServer.createContext(contextPath, new JolokiaHttpHandler(config.getJolokiaConfig()));

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

    private void initializeExecutor() {
        Executor executor;
        String mode = config.getExecutor();
        if ("fixed".equalsIgnoreCase(mode)) {
            executor = Executors.newFixedThreadPool(config.getThreadNr());
        } else if ("cached".equalsIgnoreCase(mode)) {
            executor = Executors.newCachedThreadPool();
        } else {
            executor = Executors.newSingleThreadExecutor();
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
    private static final class JolokiaHttpsConfigurator extends HttpsConfigurator {
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

