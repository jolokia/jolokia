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
package org.jolokia.client.httpclient4;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Collection;
import java.util.function.Consumer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.VersionInfo;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.spi.HttpClientBuilder;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.client.spi.HttpHeader;

/**
 * {@link HttpClientBuilder} that creates HTTP Client using Apache HttpClient 4 library.
 */
public class Http4ClientBuilder implements HttpClientBuilder<HttpClient> {

    @Override
    public HttpClientSpi<HttpClient> buildHttpClient(JolokiaClientBuilder.Configuration jcb) {
        String user = jcb.user();
        String password = jcb.password();

        org.apache.http.impl.client.HttpClientBuilder builder = HttpClients.custom()
            .setUserAgent("Jolokia JMX-Client (using Apache-HttpClient/" + getVersionInfo() + ")")
            .setDefaultCookieStore(new BasicCookieStore())
            .setDefaultRequestConfig(createRequestConfig(jcb));

        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
        registryBuilder.register("http", PlainConnectionSocketFactory.INSTANCE);

        if (jcb.tlsConfig() != null && jcb.tlsConfig().protocolVersion() != null) {
            try {
                LayeredConnectionSocketFactory sslSocketFactory = createSSLSocketFactory(jcb);
                builder.setSSLSocketFactory(sslSocketFactory);
                registryBuilder.register("https", sslSocketFactory);
            } catch (Exception e) {
                throw new IllegalArgumentException("Problem with TLS configuration: " + e.getMessage(), e);
            }
        }
        Registry<ConnectionSocketFactory> registry = registryBuilder.build();

        HttpClientConnectionManager connManager = jcb.poolConfig().usePool()
            ? createPoolingConnectionManager(jcb, registry)
            : createBasicConnectionManager(jcb, registry);

        builder.setConnectionManager(connManager);

        Collection<HttpHeader> defaultHttpHeaders = jcb.defaultHttpHeaders();
        if (defaultHttpHeaders != null && !defaultHttpHeaders.isEmpty()) {
            Collection<BasicHeader> headers = defaultHttpHeaders.stream().map(h -> new BasicHeader(h.name(), h.value())).toList();
            builder.setDefaultHeaders(headers);
        }

        if (user != null && !user.isEmpty()) {
            // Preparing the builder for the credentials
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                new AuthScope(AuthScope.ANY),
                new UsernamePasswordCredentials(user, password));
            builder.setDefaultCredentialsProvider(credentialsProvider);
            builder.addInterceptorFirst(new PreemptiveAuthInterceptor(new BasicScheme()));
        }

        setupProxyIfNeeded(jcb.proxy(), builder);

        if (jcb.customizer() != null) {
            Class<?> builderClass = jcb.clientBuilderClass();
            if (!builderClass.isAssignableFrom(builder.getClass())) {
                throw new IllegalArgumentException("Unsupported class for HttpClient4 builder associated with the customizer: " + builderClass.getName());
            }
            //noinspection unchecked
            ((Consumer<org.apache.http.impl.client.HttpClientBuilder>) jcb.customizer()).accept(builder);
        }

        // return the wrapper
        return new Http4Client(builder.build(), jcb);
    }

    private static String getVersionInfo() {
        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.http.client", Http4ClientBuilder.class.getClassLoader());
        return (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
    }

    private void setupProxyIfNeeded(JolokiaClientBuilder.Proxy httpProxy, org.apache.http.impl.client.HttpClientBuilder builder) {
        if (httpProxy != null) {
            builder.setProxy(new HttpHost(httpProxy.getHost(), httpProxy.getPort()));
            if (httpProxy.getUser() != null) {
                AuthScope proxyAuthScope = new AuthScope(httpProxy.getHost(), httpProxy.getPort());
                UsernamePasswordCredentials proxyCredentials = new UsernamePasswordCredentials(httpProxy.getUser(), httpProxy.getPass());
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(proxyAuthScope, proxyCredentials);
                builder.setDefaultCredentialsProvider(credentialsProvider);
            }
        }
    }

    private RequestConfig createRequestConfig(JolokiaClientBuilder.Configuration jcb) {
        RequestConfig.Builder builder = RequestConfig.custom();

        builder.setNormalizeUri(false);
        builder.setExpectContinueEnabled(jcb.expectContinue());
        if (jcb.connectionConfig().socketTimeout() > -1) {
            // milliseconds
            builder.setSocketTimeout(jcb.connectionConfig().socketTimeout());
        }
        if (jcb.connectionConfig().connectionTimeout() > -1) {
            // milliseconds
            builder.setConnectTimeout(jcb.connectionConfig().connectionTimeout());
        }
        if (jcb.poolConfig().usePool() && jcb.poolConfig().connectionPoolTimeout() > -1) {
            // milliseconds
            builder.setConnectionRequestTimeout(jcb.poolConfig().connectionPoolTimeout());
        }
        return builder.build();
    }

    private BasicHttpClientConnectionManager createBasicConnectionManager(JolokiaClientBuilder.Configuration jcb, Registry<ConnectionSocketFactory> registry) {
        BasicHttpClientConnectionManager connManager
            = new BasicHttpClientConnectionManager(registry, getConnectionFactory());

        connManager.setSocketConfig(createSocketConfig(jcb));
        connManager.setConnectionConfig(createConnectionConfig(jcb));

        return connManager;
    }

    private PoolingHttpClientConnectionManager createPoolingConnectionManager(JolokiaClientBuilder.Configuration jcb, Registry<ConnectionSocketFactory> registry) {
        PoolingHttpClientConnectionManager connManager
            = new PoolingHttpClientConnectionManager(registry, getConnectionFactory());
        connManager.setDefaultSocketConfig(createSocketConfig(jcb));
        connManager.setDefaultConnectionConfig(createConnectionConfig(jcb));

        if (jcb.poolConfig().maxConnections() != 0) {
            connManager.setMaxTotal(jcb.poolConfig().maxConnections());
            // there's only one route anyway.
            connManager.setMaxPerRoute(new HttpRoute(new HttpHost(jcb.url().getHost(), jcb.url().getPort())), jcb.poolConfig().maxConnections());
        }

        return connManager;
    }

    private LayeredConnectionSocketFactory createSSLSocketFactory(JolokiaClientBuilder.Configuration jcb) throws Exception {
        JolokiaClientBuilder.TlsConfiguration tlsConfiguration = jcb.tlsConfig();
        tlsConfiguration.validate();
        KeyStore ks = null;
        if (tlsConfiguration.keystore() != null) {
            ks = tlsConfiguration.keystore();
        } else if (tlsConfiguration.keystorePath() != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(tlsConfiguration.keystorePath().toFile())) {
                ks.load(fis, tlsConfiguration.keystorePassword() == null ? new char[0] : tlsConfiguration.keystorePassword().toCharArray());
                // no init - will be done by HttpClient4
            }
        }

        KeyStore ts = null;
        if (tlsConfiguration.truststore() != null) {
            ts = tlsConfiguration.truststore();
        } else if (tlsConfiguration.truststorePath() != null) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            ts = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(tlsConfiguration.truststorePath().toFile())) {
                ts.load(fis, tlsConfiguration.truststorePassword() == null ? new char[0] : tlsConfiguration.truststorePassword().toCharArray());
                tmf.init(ts);
            }
        }

        SSLContext sslcontext = SSLContexts.custom()
            .setProtocol(tlsConfiguration.protocolVersion())
            .loadKeyMaterial(ks, tlsConfiguration.keyPassword() == null ? new char[0] : tlsConfiguration.keyPassword().toCharArray())
            .loadTrustMaterial(ts, null)
            .build();

        return new SSLConnectionSocketFactory(sslcontext, new DefaultHostnameVerifier());
    }

    private ConnectionConfig createConnectionConfig(JolokiaClientBuilder.Configuration jcb) {
        return ConnectionConfig.custom()
            .setBufferSize(jcb.connectionConfig().socketBufferSize())
            .setCharset(jcb.contentCharset())
            .build();
    }

    private SocketConfig createSocketConfig(JolokiaClientBuilder.Configuration jcb) {
        SocketConfig.Builder socketConfigB = SocketConfig.custom();
        if (jcb.connectionConfig().socketTimeout() >= 0) {
            socketConfigB.setSoTimeout(jcb.connectionConfig().socketTimeout());
        }
        socketConfigB.setTcpNoDelay(jcb.connectionConfig().tcpNoDelay());
        return socketConfigB.build();
    }

    private HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> getConnectionFactory() {
        return new ManagedHttpClientConnectionFactory(new DefaultHttpRequestWriterFactory(),
            new DefaultHttpResponseParserFactory());
    }

    /**
     * Interceptor for preemptive, basic authentication authentication. Inspiration
     * taken from http://stackoverflow.com/a/3493746/207604
     */
    static class PreemptiveAuthInterceptor implements HttpRequestInterceptor {

        // Auth scheme to use
        private final AuthScheme authScheme;

        PreemptiveAuthInterceptor(AuthScheme pScheme) {
            authScheme = pScheme;
        }

        @Override
        public void process(final HttpRequest request, final HttpContext context) throws HttpException {
            AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            if (authState.getAuthScheme() == null) {
                CredentialsProvider credentialsProvider = (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
                Credentials credentials = credentialsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                if (credentials == null) {
                    throw new HttpException("No credentials given for preemptive authentication");
                }
                authState.update(authScheme, credentials);
            }
        }
    }

}
