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

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.spi.HttpClientBuilder;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.client.spi.HttpHeader;

public class JdkClientBuilder implements HttpClientBuilder<HttpClient> {

    @Override
    public HttpClientSpi<HttpClient> buildHttpClient(J4pClientBuilder.Configuration jcb) {
        // client specific properties
        String user = jcb.user();
        String password = jcb.password();
        J4pClientBuilder.Proxy httpProxy = jcb.proxy();
        int connectionTimeout = jcb.connectionTimeout();

        // ssl configuration

        // properties to be used when performing requests
        String url = jcb.url();
        int socketTimeout = jcb.socketTimeout();
        Charset contentCharset = jcb.contentCharset();
        boolean expectContinue = jcb.expectContinue();
        Collection<HttpHeader> defaultHttpHeaders = jcb.defaultHttpHeaders();

        // properties that can't be used with JDK client, unless we want to mess with global system properties
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/module-summary.html
        // jdk.httpclient.bufsize
        // jdk.httpclient.receiveBufferSize
        // jdk.httpclient.sendBufferSize
        // jdk.httpclient.connectionPoolSize
        boolean tcpNoDelay = jcb.tcpNoDelay();
        int socketBufferSize = jcb.socketBufferSize();

        HttpClient.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofMillis(connectionTimeout))
            .followRedirects(HttpClient.Redirect.NORMAL)
//            .cookieHandler(null)
//            .executor(null)
//            .sslContext(null)
//            .sslParameters(null)
            .priority(1);

        if (user != null && !user.isBlank()) {
            builder.authenticator(new Authenticator() {
                @Override
                public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                    return switch (reqType) {
                        case SERVER
                            -> new PasswordAuthentication(user, password == null ? new char[0] : password.toCharArray());
                        case PROXY
                            -> new PasswordAuthentication(httpProxy.getUser(),
                            httpProxy.getPass() == null ? new char[0] : httpProxy.getPass().toCharArray());
                    };
                }
            });
        }
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
//        if (customizer != null) {
//            customizer.configure(builder);
//        }
        HttpClient client = builder.build();

        // return the wrapper
        return new HttpClientSpi<>() {
            @Override
            public HttpClient getClient(Class<HttpClient> clientClass) {
                if (clientClass.isAssignableFrom(client.getClass())) {
                    return clientClass.cast(client);
                }
                return null;
            }

            @Override
            public HttpResponse<InputStream> execute(HttpRequest httpRequest) throws IOException {
                HttpResponse.BodyHandler<InputStream> responseHandler = HttpResponse.BodyHandlers.ofInputStream();
                try {
                    return client.send(httpRequest, responseHandler);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            }
        };
    }

}
