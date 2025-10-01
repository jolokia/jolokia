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

import java.net.URI;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.BasicHeader;
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

        return new Http5Client(builder.build(), jcb, credentialsProvider);
    }

    /**
     * Version information for HttpClient5
     * @return
     */
    private static String getVersionInfo() {
        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.hc.client5", Http5ClientBuilder.class.getClassLoader());
        return (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
    }

    /**
     * {@link HttpClientConnectionManager} may be pooling or basic and configures low-level connection options
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
        TlsConfig.Builder tcBuilder = TlsConfig.custom();
        TlsConfig tlsConfig = tcBuilder.build();

        SSLContext sslContext = SSLContexts.createSystemDefault();
//        TlsSocketStrategy tlsStrategy = new DefaultClientTlsStrategy(sslContext);
        TlsSocketStrategy tlsStrategy = DefaultClientTlsStrategy.createSystemDefault();

        Registry<TlsSocketStrategy> registry = RegistryBuilder.<TlsSocketStrategy>create()
            .register("https", tlsStrategy)
            .build();

        HttpClientConnectionManager manager;

        if (jcb.poolConfig().usePool()) {
            // pooling connection manager - with a builder
            PoolingHttpClientConnectionManager poolingManager = PoolingHttpClientConnectionManagerBuilder.create()
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

//            poolingManager.setDefaultMaxPerRoute(1);
//            poolingManager.setMaxTotal(1);
            manager = poolingManager;
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

}
