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

import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.scheme.*;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.*;
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

    // parameters to build up
    private HttpParams params;

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
        params = new BasicHttpParams();
        connectionTimeout(20 * 1000);
        maxTotalConnections(20);
        maxConnectionPoolTimeout(500);
        contentCharset(HTTP.DEFAULT_CONTENT_CHARSET);
        expectContinue(true);
        tcpNoDelay(true);
        socketBufferSize(8192);
        pooledConnections();
        user = null;
        password = null;

        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.http.client", getClass().getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
        HttpProtocolParams.setUserAgent(params,"Jolokia JMX-Client (using Apache-HttpClient/" + release +")");
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
        HttpConnectionParams.setConnectionTimeout(params,pTimeOut);
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
        HttpConnectionParams.setSoTimeout(params,pTimeOut);
        return this;
    }

    /**
     * Sets the maximum number of connections allowed when using {@link #pooledConnections()}.
     * @param pConnections number of max. simultaneous connections
     */
    public final J4pClientBuilder maxTotalConnections(int pConnections) {
        ConnManagerParams.setMaxTotalConnections(params, pConnections);
        return this;
    }

    /**
     * Sets the timeout in milliseconds used when retrieving a connection
     * from the {@link org.apache.http.conn.ClientConnectionManager}.
     *
     * @param pConnectionPoolTimeout timeout in milliseconds
     */
    public final J4pClientBuilder maxConnectionPoolTimeout(int pConnectionPoolTimeout) {
        ConnManagerParams.setTimeout(params,pConnectionPoolTimeout);
        return this;
    }

    /**
     * Defines the charset to be used per default for encoding content body.
     * @param pContentCharset the charset to use
     */
    public final J4pClientBuilder contentCharset(String pContentCharset) {
        HttpProtocolParams.setContentCharset(params, pContentCharset);
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
        HttpProtocolParams.setUseExpectContinue(params,pUse);
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
        HttpConnectionParams.setTcpNoDelay(params,pUse);
        return this;
    }

    /**
     * Determines the size of the internal socket buffer used to buffer data while receiving /
     * transmitting HTTP messages.
     * @param pSize size of socket buffer
     */
    public final J4pClientBuilder socketBufferSize(int pSize) {
        HttpConnectionParams.setSocketBufferSize(params,pSize);
        return this;
    }

    // =====================================================================================

    /**
     * Build the agent with the information given before
     *
     * @return a new J4pClient
     */
    public J4pClient build() {
        ClientConnectionManager cm = createClientConnectionManager();
        DefaultHttpClient httpClient = new DefaultHttpClient(cm, getHttpParams());
        if (user != null) {
            httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY,
                                                               new UsernamePasswordCredentials(user,password));
        }
        return new J4pClient(url,httpClient,targetUrl != null ? new J4pTargetConfig(targetUrl,targetUser,targetPassword) : null);
    }

    ClientConnectionManager createClientConnectionManager() {
        return pooledConnections ?
                new ThreadSafeClientConnManager(getHttpParams(), getSchemeRegistry()) :
                new SingleClientConnManager(getSchemeRegistry());
    }


    HttpParams getHttpParams() {
        return params;
    }

    private SchemeRegistry getSchemeRegistry() {
        // Create and initialize scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        return schemeRegistry;
    }
}
