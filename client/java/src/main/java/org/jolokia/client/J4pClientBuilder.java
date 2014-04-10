package org.jolokia.client;

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

import javax.net.ssl.SSLContext;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.*;
import org.apache.http.conn.*;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.io.DefaultHttpResponseParserFactory;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.VersionInfo;
import org.jolokia.client.request.J4pTargetConfig;

/**
 * A builder for a {@link org.jolokia.client.J4pClient}.
 *
 * @author roland
 * @since 26.11.10
 */
public class J4pClientBuilder {

    HttpClientBuilder builder;
    private int connnectionTimeout;
    private int socketTimeout;
    private int maxTotalConnections;
    private int maxConnectionPoolTimeout;
    private String contentCharset;
    private boolean expectContinue;
    private boolean tcpNoDelay;
    private int socketBufferSize;

    // whether to use thread safe, pooled connections
    private boolean pooledConnections;

    // Connection URL to use
    private String url;

    // User to use for authentication
    private String user;

    // Password to use for authentication
    private String password;

    // Service-URL when used in proxy mode
    private String targetUrl;

    // User used for JSR-160 communication when using with a proxy (i.e. targetUrl != null)
    private String targetUser;

    // Password to use for JSR-160 communication when using with a proxy (i.e. targetUrl != null and targetUser != null)
    private String targetPassword;

    /**
     * Package access constructor, use static method on J4pClient for creating
     * the builder.
     */
    public J4pClientBuilder() {
        builder = HttpClientBuilder.create();
        connectionTimeout(20 * 1000);
        maxTotalConnections(20);
        maxConnectionPoolTimeout(500);
        contentCharset(HTTP.DEF_CONTENT_CHARSET.name());
        expectContinue(true);
        tcpNoDelay(true);
        socketBufferSize(8192);
        pooledConnections();
        user = null;
        password = null;

        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.http.client", getClass().getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
        builder.setUserAgent("Jolokia JMX-Client (using Apache-HttpClient/" + release + ")");
    }


    /**
     * The Agent URL to connect to
     *
     * @param pUrl agent URL
     */
    public final J4pClientBuilder url(String pUrl) {
        url = pUrl;
        return this;
    }

    /**
     * User to use for authentication
     *
     * @param pUser user name
     */
    public final J4pClientBuilder user(String pUser) {
        user  = pUser;
        return this;
    }

    /**
     * Password for authentication
     *
     * @param pPassword password to use
     */
    public final J4pClientBuilder password(String pPassword) {
        password  = pPassword;
        return this;
    }

    /**
     * Target service URL when using the agent as a JSR-160 proxy
     *
     * @param pUrl JMX service URL for the 'real' target (that gets contacted by the agent)
     */
    public final J4pClientBuilder target(String pUrl) {
        targetUrl = pUrl;
        return this;
    }

    /**
     * Target user for proxy mode. This parameter takes only effect when a target is set.
     *
     * @param pUser User to be used for authentication in JSR-160 proxy communication
     */
    public final J4pClientBuilder targetUser(String pUser) {
        targetUser = pUser;
        return this;
    }

    /**
     * Target password for proxy mode. This parameter takes only effect when a target is set and the target user is
     * not null
     *
     * @param pPassword Password to be used for authentication in JSR-160 proxy communication
     */
    public final J4pClientBuilder targetPassword(String pPassword) {
        targetPassword = pPassword;
        return this;
    }

    /**
     * Use a {@link org.apache.http.impl.conn.SingleClientConnManager} for connecting to the agent. This
     * is not very suitable in multithreaded environements
     */
    public final J4pClientBuilder singleConnection() {
        pooledConnections = false;
        return this;
    }

    /**
     * Use a {@link org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager} for connecting to the agent, which
     * uses a pool of connections (see {@link #maxTotalConnections(int) and {@link #maxConnectionPoolTimeout(int)} for
     * tuning the pool}
     */
    public final J4pClientBuilder pooledConnections() {
        pooledConnections = true;
        return this;
    }

    /**
     * Determines the timeout in milliseconds until a connection is established. A timeout value of zero is
     * interpreted as an infinite timeout.
     * @param pTimeOut timeout in milliseconds
     */
    public final J4pClientBuilder connectionTimeout(int pTimeOut) {
        connnectionTimeout = pTimeOut;
        return this;
    }

    /**
     * Defines the socket timeout (<code>SO_TIMEOUT</code>) in milliseconds,
     * which is the timeout for waiting for data  or, put differently,
     * a maximum period inactivity between two consecutive data packets).
     * A timeout value of zero is interpreted as an infinite timeout.
     *
     * @param pTimeOut SO_TIMEOUT value in milliseconds, 0 mean no timeout at all.
     */
    public final J4pClientBuilder socketTimeout(int pTimeOut) {
        socketTimeout = pTimeOut;
        return this;
    }

    /**
     * Sets the maximum number of connections allowed when using {@link #pooledConnections()}.
     * @param pConnections number of max. simultaneous connections
     */
    public final J4pClientBuilder maxTotalConnections(int pConnections) {
        maxTotalConnections = pConnections;
        return this;
    }

    /**
     * Sets the timeout in milliseconds used when retrieving a connection
     * from the {@link org.apache.http.conn.ClientConnectionManager}.
     *
     * @param pConnectionPoolTimeout timeout in milliseconds
     */
    public final J4pClientBuilder maxConnectionPoolTimeout(int pConnectionPoolTimeout) {
        maxConnectionPoolTimeout = pConnectionPoolTimeout;
        return this;
    }

    /**
     * Defines the charset to be used per default for encoding content body.
     * @param pContentCharset the charset to use
     */
    public final J4pClientBuilder contentCharset(String pContentCharset) {
        contentCharset = pContentCharset;
        return this;
    }

    /**
     * Activates 'Expect: 100-Continue' handshake for the entity enclosing methods.
     * The purpose of the 'Expect: 100-Continue' handshake to allow a client that is
     * sending a request message with a request body to determine if the origin server
     * is willing to accept the request (based on the request headers) before the client
     * sends the request body.
     * The use of the 'Expect: 100-continue' handshake can result in noticable peformance
     * improvement for entity enclosing requests that require the target server's authentication.
     *
     * @param pUse whether to use this algorithm or not
     */
    public final J4pClientBuilder expectContinue(boolean pUse) {
        expectContinue = pUse;
        return this;
    }

    /**
     * Determines whether Nagle's algorithm is to be used. The Nagle's algorithm tries to conserve
     * bandwidth by minimizing the number of segments that are sent. When applications wish to
     * decrease network latency and increase performance, they can disable Nagle's
     * algorithm (that is enable TCP_NODELAY). Data will be sent earlier, at the cost
     * of an increase in bandwidth consumption.
     * @param pUse whether to use NO_DELAY or not
     */
    public final J4pClientBuilder tcpNoDelay(boolean pUse) {
        tcpNoDelay = pUse;
        return this;
    }

    /**
     * Determines the size of the internal socket buffer used to buffer data while receiving /
     * transmitting HTTP messages.
     * @param pSize size of socket buffer
     */
    public final J4pClientBuilder socketBufferSize(int pSize) {
        socketBufferSize = pSize;
        return this;
    }

    // =====================================================================================

    /**
     * Build the agent with the information given before
     *
     * @return a new J4pClient
     */
    public J4pClient build() {
        return new J4pClient(url,createHttpClient(),targetUrl != null ? new J4pTargetConfig(targetUrl,targetUser,targetPassword) : null);
    }

    private Registry<ConnectionSocketFactory> getSocketFactoryRegistry() {
        SSLContext sslcontext = SSLContexts.createSystemDefault();
        X509HostnameVerifier hostnameVerifier = new BrowserCompatHostnameVerifier();

        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", new SSLConnectionSocketFactory(sslcontext, hostnameVerifier))
                .build();
    }

    public HttpClient createHttpClient() {

        RequestConfig requestConfig = RequestConfig.custom()
                .setExpectContinueEnabled(expectContinue)
                .setSocketTimeout(socketTimeout)
                .setConnectionRequestTimeout(connnectionTimeout)
                .build();

        HttpClientConnectionManager connManager = createHttpClientConnectionManager();

        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig);


        if (user != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(AuthScope.ANY),
                    new UsernamePasswordCredentials(user, password));
            builder.setDefaultCredentialsProvider(credentialsProvider);
        }

        return builder.build();
    }


    private HttpClientConnectionManager createHttpClientConnectionManager() {
        SocketConfig socketConfig = SocketConfig.custom()
                .setTcpNoDelay(tcpNoDelay)
                .build();

        if (pooledConnections) {
            PoolingHttpClientConnectionManager connManager =
                new PoolingHttpClientConnectionManager(getSocketFactoryRegistry(), getConnectionFactory());
            connManager.setDefaultSocketConfig(socketConfig);
            return connManager;
        }
        return null; //TODO!!
    }

    public HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> getConnectionFactory() {
        return new ManagedHttpClientConnectionFactory(new DefaultHttpRequestWriterFactory(), new DefaultHttpResponseParserFactory());
    }
}
