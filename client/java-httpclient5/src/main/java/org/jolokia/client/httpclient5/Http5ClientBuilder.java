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
package org.jolokia.client.httpclient5;

import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.VersionInfo;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.spi.HttpClientBuilder;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.client.spi.HttpHeader;

/**
 * {@link HttpClientBuilder} that creates HTTP Client using Apache HttpClient 4 library.
 */
public class Http5ClientBuilder implements HttpClientBuilder<HttpClient> {

    @Override
    public HttpClientSpi<HttpClient> buildHttpClient(JolokiaClientBuilder.Configuration jcb) {
        String user = jcb.user();
        String password = jcb.password();
        JolokiaClientBuilder.Proxy httpProxy = jcb.proxy();
        URI jolokiaAgentUrl = jcb.url();

        // see: https://github.com/apache/httpcomponents-client/blob/master/httpclient5/src/test/java/org/apache/hc/client5/http/examples/ClientConfiguration.java

        org.apache.hc.client5.http.impl.classic.HttpClientBuilder builder = HttpClients.custom()
            .setUserAgent("Jolokia JMX-Client (using Apache-HttpClient/" + getVersionInfo() + ")")
            .setConnectionManager(createConnectionManager(jcb))
            .setDefaultCookieStore(new BasicCookieStore())
            .setDefaultRequestConfig(createRequestConfig(jcb));

        // Default headers
        Collection<HttpHeader> defaultHttpHeaders = jcb.defaultHttpHeaders();
        if (defaultHttpHeaders != null && !defaultHttpHeaders.isEmpty()) {
            Collection<BasicHeader> headers = defaultHttpHeaders.stream()
                .map(h -> new BasicHeader(h.name(), h.value()))
                .toList();
            builder.setDefaultHeaders(headers);
        }

        // used for target and for proxy
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        boolean credentialsProvided = false;

        if (user != null && !user.isEmpty()) {
            // Preparing the builder for the credentials
            AuthScope targetScope = new AuthScope(jolokiaAgentUrl.getHost(), jolokiaAgentUrl.getPort());
            credentialsProvider.setCredentials(targetScope, new UsernamePasswordCredentials(user, password.toCharArray()));
            credentialsProvided = true;
        }

        if (jcb.user() != null && !jcb.user().isEmpty()) {
            builder.addRequestInterceptorFirst(new PreemptiveAuthRequestInterceptor(jcb));
        }

        // Proxy
        if (httpProxy != null) {
            HttpHost proxy = new HttpHost(httpProxy.getHost(), httpProxy.getPort());
            builder.setProxy(proxy);

            if (httpProxy.getUser() != null) {
                AuthScope proxyScope = new AuthScope(httpProxy.getHost(), httpProxy.getPort());
                credentialsProvider.setCredentials(proxyScope, new UsernamePasswordCredentials(httpProxy.getUser(),
                    httpProxy.getPass().toCharArray()));
                credentialsProvided = true;
            }
        }

        if (credentialsProvided) {
            builder.setDefaultCredentialsProvider(credentialsProvider);
        }

        if (jcb.customizer() != null) {
            Class<?> builderClass = jcb.clientBuilderClass();
            if (!builderClass.isAssignableFrom(builder.getClass())) {
                throw new IllegalArgumentException("Unsupported class for HttpClient5 builder associated with the customizer: " + builderClass.getName());
            }
            //noinspection unchecked
            ((Consumer<org.apache.hc.client5.http.impl.classic.HttpClientBuilder>) jcb.customizer()).accept(builder);
        }

        return new Http5Client(builder.build(), jcb, credentialsProvider);
    }

    /**
     * Version information for HttpClient5
     *
     * @return
     */
    private static String getVersionInfo() {
        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.hc.client5", Http5ClientBuilder.class.getClassLoader());
        return (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
    }

    /**
     * {@link HttpClientConnectionManager} may be pooling or basic and configures low-level connection options
     *
     * @param jcb
     * @return
     */
    private HttpClientConnectionManager createConnectionManager(JolokiaClientBuilder.Configuration jcb) {
        // connection config
        ConnectionConfig.Builder ccBuilder = ConnectionConfig.custom();
        JolokiaClientBuilder.ConnectionConfiguration jcbConnectionConfig = jcb.connectionConfig();
        if (jcbConnectionConfig.connectionTimeout() != -1) {
            ccBuilder.setConnectTimeout(jcbConnectionConfig.connectionTimeout(), TimeUnit.MILLISECONDS);
        }
        if (jcbConnectionConfig.socketTimeout() != -1) {
            ccBuilder.setSocketTimeout(jcbConnectionConfig.socketTimeout(), TimeUnit.MILLISECONDS);
        }
        ConnectionConfig connectionConfig = ccBuilder.build();

        // socket config
        SocketConfig.Builder scBuilder = SocketConfig.custom();
        scBuilder.setRcvBufSize(jcbConnectionConfig.socketBufferSize());
        scBuilder.setSndBufSize(jcbConnectionConfig.socketBufferSize());
        if (jcbConnectionConfig.socketTimeout() != -1) {
            scBuilder.setSoTimeout(jcbConnectionConfig.socketTimeout(), TimeUnit.MILLISECONDS);
        }
        scBuilder.setTcpNoDelay(jcbConnectionConfig.tcpNoDelay());
        SocketConfig socketConfig = scBuilder.build();

        // TLS config
        TlsConfig tlsConfig = null;
        SSLContext sslContext;
        TlsSocketStrategy tlsStrategy = null;

        RegistryBuilder<TlsSocketStrategy> registryBuilder = RegistryBuilder.create();
        Registry<TlsSocketStrategy> registry;

        if (jcb.tlsConfig() != null && jcb.tlsConfig().protocolVersion() != null) {
            TlsConfig.Builder tcBuilder = TlsConfig.custom();
            tcBuilder.setSupportedProtocols(jcb.tlsConfig().protocolVersion());
            tlsConfig = tcBuilder.build();

            try {
                sslContext = createSSLSocketFactory(jcb);
                tlsStrategy = new DefaultClientTlsStrategy(sslContext);

                registryBuilder.register("https", tlsStrategy);

            } catch (Exception e) {
                throw new IllegalArgumentException("Problem with TLS configuration: " + e.getMessage(), e);
            }
        }
        registry = registryBuilder.build();

        HttpClientConnectionManager manager;

        if (jcb.poolConfig().usePool()) {
            // pooling connection manager - with a builder
            manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSchemePortResolver(DefaultSchemePortResolver.INSTANCE)
                .setDnsResolver(SystemDefaultDnsResolver.INSTANCE)
                .setConnectionFactory(ManagedHttpClientConnectionFactory.INSTANCE)
                .setDefaultConnectionConfig(connectionConfig)
                .setDefaultSocketConfig(socketConfig)
                .setMaxConnTotal(jcb.poolConfig().maxConnections())
                .setMaxConnPerRoute(jcb.poolConfig().maxConnections())
                .setTlsSocketStrategy(tlsStrategy)
                .setDefaultTlsConfig(tlsConfig)
                .build();
        } else {
            // basic connection manager - no builder
            BasicHttpClientConnectionManager basicManager = BasicHttpClientConnectionManager.create(
                DefaultSchemePortResolver.INSTANCE, SystemDefaultDnsResolver.INSTANCE,
                registry, ManagedHttpClientConnectionFactory.INSTANCE
            );
            basicManager.setConnectionConfig(connectionConfig);
            basicManager.setSocketConfig(socketConfig);
            basicManager.setTlsConfig(tlsConfig);
            manager = basicManager;
        }

        return manager;
    }

    private RequestConfig createRequestConfig(JolokiaClientBuilder.Configuration jcb) {
        RequestConfig.Builder builder = RequestConfig.custom();
        builder.setExpectContinueEnabled(jcb.expectContinue());
        if (jcb.connectionConfig().socketTimeout() > -1) {
            // milliseconds
            builder.setResponseTimeout(jcb.connectionConfig().socketTimeout(), TimeUnit.MILLISECONDS);
        }
        if ((jcb.poolConfig().usePool())) {
            if (jcb.poolConfig().connectionPoolTimeout() > -1) {
                builder.setConnectionRequestTimeout(jcb.poolConfig().connectionPoolTimeout(), TimeUnit.MILLISECONDS);
            }
        }
        return builder.build();
    }

    private SSLContext createSSLSocketFactory(JolokiaClientBuilder.Configuration jcb) throws Exception {
        JolokiaClientBuilder.TlsConfiguration tlsConfiguration = jcb.tlsConfig();
        KeyStore ks = null;
        if (tlsConfiguration.keystore() != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(tlsConfiguration.keystore().toFile())) {
                ks.load(fis, tlsConfiguration.keystorePassword() == null ? new char[0] : tlsConfiguration.keystorePassword().toCharArray());
                // no init - will be done by HttpClient4
            }
        }

        KeyStore ts = null;
        if (tlsConfiguration.truststore() != null) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            ts = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(tlsConfiguration.truststore().toFile())) {
                ts.load(fis, tlsConfiguration.truststorePassword() == null ? new char[0] : tlsConfiguration.truststorePassword().toCharArray());
                tmf.init(ts);
            }
        }

        return SSLContexts.custom()
            .setProtocol(tlsConfiguration.protocolVersion())
            .loadKeyMaterial(ks, tlsConfiguration.keyPassword() == null ? new char[0] : tlsConfiguration.keyPassword().toCharArray())
            .loadTrustMaterial(ts, null)
            .build();
    }

    private static class PreemptiveAuthRequestInterceptor implements HttpRequestInterceptor {
        private final String authorization;

        public PreemptiveAuthRequestInterceptor(JolokiaClientBuilder.Configuration jcb) {
            String credentials = jcb.user() + ":" + (jcb.password() == null ? "" : jcb.password());
            authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void process(HttpRequest request, EntityDetails entity, HttpContext context) {
            request.setHeader("Authorization", authorization);
            context.getAttribute("");
        }
    }

}
