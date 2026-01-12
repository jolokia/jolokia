/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client.jdkclient;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.spi.HttpClientBuilder;
import org.jolokia.client.spi.HttpClientSpi;

/**
 * {@link HttpClientBuilder} that creates {@link HttpClientSpi} based on {@link HttpClient JDK HTTP Client}.
 */
public class JdkHttpClientBuilder implements HttpClientBuilder<HttpClient> {

    @Override
    public HttpClientSpi<HttpClient> buildHttpClient(JolokiaClientBuilder.Configuration jcb) {
        // properties that can't be used with JDK client, unless we want to mess with global system properties
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/module-summary.html
        // jdk.httpclient.bufsize
        // jdk.httpclient.receiveBufferSize
        // jdk.httpclient.sendBufferSize
        // jdk.httpclient.connectionPoolSize

        HttpClient.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
//            .sslParameters(null)
//            .executor(null)
            .priority(1);

        if (jcb.tlsConfig() != null && jcb.tlsConfig().protocolVersion() != null) {
            try {
                builder.sslContext(createSSLContest(jcb.tlsConfig()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Problem with TLS configuration: " + e.getMessage(), e);
            }
        }

        if (jcb.connectionConfig().connectionTimeout() != -1) {
            builder.connectTimeout(Duration.ofMillis(jcb.connectionConfig().connectionTimeout()));
        }

        // instead of relying on java.net.http.HttpClient.Builder.authenticator(), we will force preemptive
        // authentication by manually sending the Authorization header.
        // If we set the authenticator, JDK HTTP Client will remove Authorization and Proxy-Authorization headers
        // see: jdk.internal.net.http.common.Utils.CONTEXT_RESTRICTED
//        if (user != null && !user.isBlank()) {
//            builder.authenticator(new Authenticator() {
//                @Override
//                public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
//                    return switch (reqType) {
//                        case SERVER
//                            -> new PasswordAuthentication(user, password == null ? new char[0] : password.toCharArray());
//                        case PROXY
//                            -> new PasswordAuthentication(httpProxy.getUser(),
//                            httpProxy.getPass() == null ? new char[0] : httpProxy.getPass().toCharArray());
//                    };
//                }
//            });
//        }

        JolokiaClientBuilder.Proxy httpProxy = jcb.proxy();
        if (httpProxy != null) {
            // java.net.ProxySelector.of() will try to resolve the host during configuration.
            builder.proxy(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                        new InetSocketAddress(httpProxy.getHost(), httpProxy.getPort())));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                }
            });
        }

        if (jcb.customizer() != null) {
            Class<?> builderClass = jcb.clientBuilderClass();
            if (!builderClass.isAssignableFrom(builder.getClass())) {
                throw new IllegalArgumentException("Unsupported class for JDK Client builder associated with the customizer: " + builderClass.getName());
            }
            //noinspection unchecked
            ((Consumer<HttpClient.Builder>) jcb.customizer()).accept(builder);
        }

        // return the wrapper
        return new JdkHttpClient(builder.build(), jcb);
    }

    private SSLContext createSSLContest(JolokiaClientBuilder.TlsConfiguration tlsConfiguration) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
        if (tlsConfiguration.protocolVersion() == null) {
            return null;
        }
        tlsConfiguration.validate();
        SSLContext context = SSLContext.getInstance(tlsConfiguration.protocolVersion());

        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        if (tlsConfiguration.keystore() != null) {
            tlsConfiguration.keystore().load(null, tlsConfiguration.keystorePassword() == null ? new char[0] : tlsConfiguration.keystorePassword().toCharArray());
            kmf.init(tlsConfiguration.keystore(), tlsConfiguration.keyPassword() == null ? new char[0] : tlsConfiguration.keyPassword().toCharArray());
        } else if (tlsConfiguration.keystorePath() != null) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(tlsConfiguration.keystorePath().toFile())) {
                ks.load(fis, tlsConfiguration.keystorePassword() == null ? new char[0] : tlsConfiguration.keystorePassword().toCharArray());
                kmf.init(ks, tlsConfiguration.keyPassword() == null ? new char[0] : tlsConfiguration.keyPassword().toCharArray());
            }
            keyManagers = kmf.getKeyManagers();
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        if (tlsConfiguration.truststore() != null) {
            tlsConfiguration.truststore().load(null, tlsConfiguration.truststorePassword() == null ? new char[0] : tlsConfiguration.truststorePassword().toCharArray());
            tmf.init(tlsConfiguration.truststore());
        } else if (tlsConfiguration.truststorePath() != null) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(tlsConfiguration.truststorePath().toFile())) {
                ks.load(fis, tlsConfiguration.truststorePassword() == null ? new char[0] : tlsConfiguration.truststorePassword().toCharArray());
                tmf.init(ks);
            }
            trustManagers = tmf.getTrustManagers();
        }

        context.init(keyManagers, trustManagers, SecureRandom.getInstance("SHA1PRNG"));

        return context;
    }

}
