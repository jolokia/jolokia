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
package org.jolokia.client;

import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.jolokia.client.jdkclient.JdkHttpClientBuilder;
import org.jolokia.client.response.J4pResponseExtractor;
import org.jolokia.client.response.ValidatingResponseExtractor;
import org.jolokia.client.spi.HttpClientBuilder;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.client.spi.HttpHeader;

/**
 * <p>A builder that creates a {@link JolokiaClient}.</p>
 *
 * <p>The actual implementation will be discovered using {@link java.util.ServiceLoader} mechanism on first
 * demand, when the builder is loaded.</p>
 *
 * @author roland
 * @since 26.11.10
 */
public class JolokiaClientBuilder {

    private static HttpClientBuilder<?> httpClientBuilder = null;

    // Universal properties, that can be configured for every client implementation are configured
    // with property methods of the builder

    // socket/tcp options

    /**
     * Connection timeout in milliseconds.
     * In blocking mode it is ultimately passed to {@link java.net.Socket#connect(SocketAddress, int)} method.
     * In NIO, it's used as a timeout waiting for {@link SelectionKey#OP_CONNECT}.
     */
    private int connectionTimeout = 20_000;

    /**
     * Socket/read/write timeout in milliseconds.
     * In blocking mode it is ultimately passed to {@link java.net.Socket#setSoTimeout(int)} method.
     * In NIO, it's used as a timeout waiting for {@link SelectionKey#OP_READ}.{@link SelectionKey#OP_WRITE}.
     */
    private int socketTimeout = 0;

    /**
     * {@link java.net.SocketOptions#TCP_NODELAY} option, defaults to {@code false} which means <em>delay</em>
     * sending the data until there's more data, so less frames need to be send. This increases delay a bit, but
     * should be fine unless you want to quickly send 1 octet at a time. But we're not emulating terminals here.
     */
    private boolean tcpNoDelay = false;

    /**
     * Buffer size for reading/writing data. Various implementations may use this value differently. It can
     * be passed down to {@link java.net.Socket#setSendBufferSize(int)}, as {@link java.net.SocketOptions#SO_SNDBUF}
     * or otherwise.
     */
    private int socketBufferSize;

    // TLS options

    // Add socket factories to tune
//    private ConnectionSocketFactory sslConnectionSocketFactory;

    // HTTP options

    /**
     * {@link Charset} used to create {@link Charset#newEncoder()}/{@link Charset#newDecoder()} for
     * outgoing/incoming data.
     */
    private Charset contentCharset;

    /**
     * Target based Jolokia URL to use. For example when the target Jolokia agent responds with {@code version}
     * response at {@code http://localhost:8778/jolokia/version}, the base URL should be
     * {@code http://localhost:8778/jolokia} or {@code http://localhost:8778/jolokia/} (some target servers may
     * not handle root URL access properly)
     */
    private URI url;

    /**
     * For {@code basic} authentication, we can specify user credentials. For other authentication mechanisms,
     * dedicated customizer should be used in implementation-specific configuration.
     */
    private String user;

    /**
     * Password for {@code basic} authentication.
     */
    private String password;

    /**
     * HTTP proxy settings when accessing target Jolokia Agent URL. It may be configured directly or using
     * system properties/environment variables. TODO: check -Dhttp.proxyHost, -Dhttp.nonProxyHosts, ...
     */
    private Proxy httpProxy;

    /**
     * When set to {@code true}, POST requests will send {@code Expect: 100-continue} headers first. Data will
     * be send only after successful {@link HTTP/1.1 100 Continue}.
     */
    private boolean expectContinue;

    /**
     * Collection of additional HTTP headers to send to target Jolokia agent - specified as a collection of
     * name-value pairs.
     */
    private Collection<HttpHeader> defaultHttpHeaders;

    // Extractor used creating responses
    private J4pResponseExtractor responseExtractor;

    // JMX options, when connecting via HTTP to one Jolokia Agent used in proxy mode, where Jolokia uses
    // standard JMX remote connection to ultimate JVM which doesn't run Jolokia agent on its own.

    // Service-URL when used in proxy mode
    private String targetUrl;

    // User used for JSR-160 communication when using with a proxy (i.e. targetUrl != null)
    private String targetUser;

    // Password to use for JSR-160 communication when using with a proxy (i.e. targetUrl != null and targetUser != null)
    private String targetPassword;

//    // whether to use thread safe, pooled connections
//    private boolean pooledConnections;
//    private int maxTotalConnections;
//    private int defaultMaxConnectionsPerRoute;
//    private int maxConnectionPoolTimeout;
//
//    // Cookie store to use, might contain already prepared cookies used for a login
//    private CookieStore cookieStore;
//
//    // Authenticator to use for performing a login
//    private J4pClientCustomizer customizer;

    /**
     * Package access constructor, use static method on JolokiaClient for creating
     * the builder.
     */
    public JolokiaClientBuilder() {
        connectionTimeout(20 * 1000);
        socketTimeout(-1);
        tcpNoDelay(false);
        socketBufferSize(8192);
        contentCharset(StandardCharsets.UTF_8.name());
        expectContinue(true);
        responseExtractor(ValidatingResponseExtractor.DEFAULT);
//        maxTotalConnections(20);
//        defaultMaxConnectionsPerRoute(20);
//        maxConnectionPoolTimeout(500);
//        pooledConnections();
//        cookieStore(new BasicCookieStore());
//        authenticator(new BasicClientCustomizer());

        try {
            @SuppressWarnings("rawtypes")
            // Load with TCCL
            Optional<HttpClientBuilder> clientBuilder = ServiceLoader.load(HttpClientBuilder.class).findFirst();
            if (clientBuilder.isEmpty()) {
                // Load with App classloader
                clientBuilder = ServiceLoader.load(HttpClientBuilder.class, null).findFirst();
            }
            // discovered, default builder based on JDK HTTP Client
            httpClientBuilder = clientBuilder.orElseGet(JdkHttpClientBuilder::new);
        } catch (ServiceConfigurationError ignored) {
            httpClientBuilder = new JdkHttpClientBuilder();
        }
    }

    /**
     * The Agent URL to connect to
     *
     * @param pUrl agent URL
     */
    public final JolokiaClientBuilder url(String pUrl) {
        url = URI.create(pUrl);
        return this;
    }

    /**
     * The Agent URL to connect to
     *
     * @param pUrl agent URL
     */
    public final JolokiaClientBuilder url(URI pUrl) {
        url = pUrl;
        return this;
    }

    /**
     * User to use for authentication
     *
     * @param pUser user name
     */
    public final JolokiaClientBuilder user(String pUser) {
        user  = pUser;
        return this;
    }

    /**
     * Password for authentication
     *
     * @param pPassword password to use
     */
    public final JolokiaClientBuilder password(String pPassword) {
        password  = pPassword;
        return this;
    }

    /**
     * Target service URL when using the agent as a JSR-160 proxy
     *
     * @param pUrl JMX service URL for the 'real' target (that gets contacted by the agent)
     */
    public final JolokiaClientBuilder target(String pUrl) {
        targetUrl = pUrl;
        return this;
    }

    /**
     * Target user for proxy mode. This parameter takes only effect when a target is set.
     *
     * @param pUser User to be used for authentication in JSR-160 proxy communication
     */
    public final JolokiaClientBuilder targetUser(String pUser) {
        targetUser = pUser;
        return this;
    }

    /**
     * Target password for proxy mode. This parameter takes only effect when a target is set and the target user is
     * not null
     *
     * @param pPassword Password to be used for authentication in JSR-160 proxy communication
     */
    public final JolokiaClientBuilder targetPassword(String pPassword) {
        targetPassword = pPassword;
        return this;
    }

//    /**
//     * Use a single threaded client for connecting to the agent. This
//     * is not very suitable in multithreaded environments
//     */
//    public final J4pClientBuilder singleConnection() {
//        pooledConnections = false;
//        return this;
//    }
//
//    /**
//     * Use a pooled connection manager for connecting to the agent, which
//     * uses a pool of connections (see {@link #maxTotalConnections(int), {@link #maxConnectionPoolTimeout(int) {@link #defaultMaxConnectionsPerRoute}} for
//     * tuning the pool}
//     */
//    public final J4pClientBuilder pooledConnections() {
//        pooledConnections = true;
//        return this;
//    }

    /**
     * Determines the timeout in milliseconds until a connection is established. A timeout value of zero is
     * interpreted as an infinite timeout. Timeout value of -1 means <em>use http client default</em>.
     * Default is 20 seconds.
     *
     * @param pTimeOut timeout in milliseconds
     */
    public final JolokiaClientBuilder connectionTimeout(int pTimeOut) {
        connectionTimeout = pTimeOut;
        return this;
    }

    /**
     * Defines the socket timeout (<code>SO_TIMEOUT</code>) in milliseconds,
     * which is the timeout for waiting for data  or, put differently,
     * a maximum period inactivity between two consecutive data packets).
     * A timeout value of zero is interpreted as an infinite timeout, a negative value means the system default.
     *
     * @param pTimeOut SO_TIMEOUT value in milliseconds, 0 mean no timeout at all. Default value is 0.
     */
    public final JolokiaClientBuilder socketTimeout(int pTimeOut) {
        socketTimeout = pTimeOut;
        return this;
    }

//    /**
//     * Sets the maximum number of connections allowed when using {@link #pooledConnections()}.
//     * @param pConnections number of max. simultaneous connections.
//     */
//    public final J4pClientBuilder maxTotalConnections(int pConnections) {
//        maxTotalConnections = pConnections;
//        return this;
//    }
//
//    /**
//     * Sets the maximum number of connections per route allowed when using {@link #pooledConnections()}
//     * @param pDefaultMaxConnectionsPerRoute number of max connections per route.
//     */
//    public final J4pClientBuilder defaultMaxConnectionsPerRoute(int pDefaultMaxConnectionsPerRoute) {
//        defaultMaxConnectionsPerRoute = pDefaultMaxConnectionsPerRoute;
//        return this;
//    }
//
//    /**
//     * Sets the timeout in milliseconds used when retrieving a connection
//     * from the connection manager. Default is 500ms, if set to -1 the system default is used. Use
//     * 0 for an infinite timeout.
//     *
//     * @param pConnectionPoolTimeout timeout in milliseconds
//     */
//    public final J4pClientBuilder maxConnectionPoolTimeout(int pConnectionPoolTimeout) {
//        maxConnectionPoolTimeout = pConnectionPoolTimeout;
//        return this;
//    }

    /**
     * Defines the charset to be used per default for encoding content body.
     * @param pContentCharset the charset to use
     */
    public final JolokiaClientBuilder contentCharset(String pContentCharset) {
        return contentCharset(Charset.forName(pContentCharset));
    }

    /**
     * Defines the charset to be used per default for encoding content body.
     * @param pContentCharset the charset to use
     */
    public final JolokiaClientBuilder contentCharset(Charset pContentCharset) {
        contentCharset = pContentCharset;
        return this;
    }

    /**
     * <p>Activates {@code Expect: 100-Continue} handshake for the entity enclosing methods.
     * The purpose of the {@code Expect: 100-Continue} handshake is to allow a client that is
     * sending a request message with a request body to determine if the origin server
     * is willing to accept the request (based on the request headers) before the client
     * sends the request body.</p>
     *
     * <p>The use of the {@code Expect: 100-continue} handshake can result in noticeable performance
     * improvement for entity enclosing requests that require the target server's authentication.</p>
     *
     * @param pUse whether to use this algorithm or not
     */
    public final JolokiaClientBuilder expectContinue(boolean pUse) {
        expectContinue = pUse;
        return this;
    }

    /**
     * Determines whether Nagle's algorithm is to be used. The Nagle's algorithm tries to conserve
     * bandwidth by minimizing the number of segments that are sent. When applications wish to
     * decrease network latency and increase performance, they can disable Nagle's
     * algorithm (that is enable {@code TCP_NODELAY}). Data will be sent earlier, at the cost
     * of an increase in bandwidth consumption.
     *
     * @param pUse whether to use NO_DELAY or not
     */
    public final JolokiaClientBuilder tcpNoDelay(boolean pUse) {
        tcpNoDelay = pUse;
        return this;
    }

    /**
     * Determines the size of the internal socket buffer used to buffer data while receiving /
     * transmitting HTTP messages.
     * @param pSize size of socket buffer
     */
    public final JolokiaClientBuilder socketBufferSize(int pSize) {
        socketBufferSize = pSize;
        return this;
    }

//    /**
//     * Set the authenticator for this client
//     *
//     * @param pAuthenticator authenticator used for checking the given user and password (if any).
//     */
//    public final J4pClientBuilder authenticator(J4pClientCustomizer<T> pAuthenticator) {
//        customizer = pAuthenticator;
//        return this;
//    }

    /**
     * Set the proxy for this client
     *
     * @param pProxy proxy definition in the format <code>http://user:pass@host:port</code> or <code>http://host:port</code>
     *               Example:   <code>http://tom:sEcReT@my.proxy.com:8080</code>
     */
    public final JolokiaClientBuilder proxy(String pProxy) {
        httpProxy = parseProxySettings(pProxy);
        return this;
    }

    /**
     * Set the proxy for this client
     *
     * @param pProxyHost proxy hostname
     * @param pProxyPort proxy port number
     */
    public final JolokiaClientBuilder proxy(String pProxyHost, int pProxyPort) {
        httpProxy = new Proxy(pProxyHost,pProxyPort);
        return this;
    }

    /**
     * Set the proxy for this client
     *
     * @param pProxyHost  proxy hostname
     * @param pProxyPort  proxy port number
     * @param pProxyUser  proxy authentication username
     * @param pProxyPass  proxy authentication password
     */
    public final JolokiaClientBuilder proxy(String pProxyHost, int pProxyPort, String pProxyUser, String pProxyPass) {
        httpProxy = new Proxy(pProxyHost,pProxyPort, pProxyUser,pProxyPass);
        return this;
    }

    /**
     * Set the proxy for this client based on http_proxy system environment variable
     */
    public final JolokiaClientBuilder useProxyFromEnvironment(){
        Map<String, String> env = System.getenv();
        for (String key : env.keySet()) {
            if (key.equalsIgnoreCase("http_proxy")){
                httpProxy = parseProxySettings(env.get(key));
                break;
            }
        }
        return this;
    }

    /**
     * Set the response extractor to use for handling single responses. By default the JSON answer from
     * the agent is parsed and only considered as successful if the status code returned is 200. In all other
     * cases an exception is thrown. An alternative extractor e.g. could silently ignored non existent MBeans (which
     * might be considered optional.
     *
     * @param pResponseExtractor response extractor to use.
     */
    public final JolokiaClientBuilder responseExtractor(J4pResponseExtractor pResponseExtractor) {
        this.responseExtractor = pResponseExtractor;
        return this;
    }

//    /**
//     * Set the SSL connection factory to use when connecting via SSL. This can be used to tune
//     * the SSL setup (SSLv3, TLSv1.2...),
//     *
//     * @param pSslConnectionSocketFactory the SSL connection factory to use
//     * @return this builder object
//     */
//    public final J4pClientBuilder sslConnectionSocketFactory(ConnectionSocketFactory pSslConnectionSocketFactory) {
//        this.sslConnectionSocketFactory = pSslConnectionSocketFactory;
//        return this;
//    }

    /**
     * Set the default HTTP Headers for each HTTP requests.
     * @param pHttpHeaders http headers to set
     * @return this builder object
     */
    public final JolokiaClientBuilder setDefaultHttpHeaders(Collection<HttpHeader> pHttpHeaders) {
        this.defaultHttpHeaders = pHttpHeaders;
        return this;
    }

    // =====================================================================================

    /**
     * Build the agent with the information given before
     *
     * @return a new JolokiaClient
     */
    public JolokiaClient build() {
        return new JolokiaClient(url, createHttpClient(),
            targetUrl != null ? new JolokiaTargetConfig(targetUrl, targetUser, targetPassword) : null,
            responseExtractor);
    }

    HttpClientSpi<?> createHttpClient() {
        return httpClientBuilder.buildHttpClient(new Configuration(url, user, password,
            httpProxy, connectionTimeout, socketTimeout, tcpNoDelay, socketBufferSize, contentCharset, expectContinue,
            defaultHttpHeaders));
    }

    /**
     * Parse proxy specification and return a proxy object representing the proxy configuration.
     * @param spec specification of for a proxy
     * @return proxy object or null if none is set
     */
    static Proxy parseProxySettings(String spec) {
        try {
            if (spec == null || spec.isEmpty()) {
                return null;
            }
            return new Proxy(spec);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Configuration DTO to pass to implementation-specific builder of actual HTTP Client. Only relevant properties
     * are passed.
     *
     * @param url                URI for the remote Jolokia Agent.
     * @param user               Basic authentication user name
     * @param password           Basic authentication password
     * @param proxy              HTTP proxy configuration
     * @param connectionTimeout  connection timeout in milliseconds used when establishing HTTP connection. Defaults to 20s.
     * @param socketTimeout      socket/request timeout in milliseconds. Defaults to 0 (infinite).
     * @param tcpNoDelay         TCP_NODELAY option
     * @param socketBufferSize   socket and buffer size to use
     * @param contentCharset     charset to use when sending the request
     * @param expectContinue     whether to send {@code Expect: 100-continue} before sending POST data.
     * @param defaultHttpHeaders collection of headers to send with each request
     */
    public record Configuration(URI url, String user, String password, Proxy proxy,
                                int connectionTimeout, int socketTimeout, boolean tcpNoDelay, int socketBufferSize,
                                Charset contentCharset, boolean expectContinue,
                                Collection<HttpHeader> defaultHttpHeaders) {

        public static Configuration withUrl(URI url) {
            return new Configuration(url, null, null, null, 5000, 5000, false, 8192, StandardCharsets.UTF_8,
                false, Collections.emptySet());
        }
    }

    /**
     * Internal representation of an HTTP proxy server. It may contain basic authentication credentials.
     * Package protected so that it can be accessed by tests.
     */
    public static class Proxy {
        private final String host;
        private final int port;
        private String user;
        private String pass;

        public Proxy(String host, int port) {
            this(host,port,null,null);
        }

        public Proxy(String host, int port, String user, String pass) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass;
        }

        /**
         * Create a proxy object from the environment
         *
         * @param env environment variable to parse
         * @throws URISyntaxException if the given env var is not a valid proxy specification
         */
        public Proxy(String env) throws URISyntaxException {
            String colon = ":";

            URI uri = new URI(env);
            this.host = uri.getHost();
            this.port = uri.getPort();

            if (host == null || host.isEmpty() || port < 0 || port > 65535) {
                throw new URISyntaxException(env, "Invalid host '" + host + "' or port " + port);
            }

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()){
                if(userInfo.contains(colon)){
                    this.user = userInfo.substring(0,userInfo.indexOf(colon));
                    this.pass = userInfo.substring(userInfo.indexOf(colon)+1);
                } else {
                    this.user = userInfo;
                }
            }
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getUser() {
            return user;
        }

        public String getPass() {
            return pass;
        }
    }

}
