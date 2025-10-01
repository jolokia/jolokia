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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

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
//            .cookieHandler(null)
//            .executor(null)
//            .sslContext(null)
//            .sslParameters(null)
            .priority(1);

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
        // TODO: customizer for the builder (not for the built client)
//        if (customizer != null) {
//            customizer.configure(builder);
//        }

        // return the wrapper
        return new JdkHttpClient(builder.build(), jcb);
    }

}
