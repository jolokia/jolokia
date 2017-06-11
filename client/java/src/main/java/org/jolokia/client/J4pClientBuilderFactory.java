package org.jolokia.client;

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

import org.apache.http.client.CookieStore;
import org.jolokia.client.request.J4pResponseExtractor;

/**
 * Hook class for easily creating J4pClient with the a builder.
 *
 * @author roland
 * @since 23.09.11
 */
public abstract class J4pClientBuilderFactory {

    protected J4pClientBuilderFactory() {}

    /** See {@link J4pClientBuilder#url} */
    public static J4pClientBuilder url(String pUrl) {
        return new J4pClientBuilder().url(pUrl);
    }

    /** See {@link J4pClientBuilder#user} */
    public static J4pClientBuilder user(String pUser) {
        return new J4pClientBuilder().user(pUser);
    }

    /** See {@link J4pClientBuilder#password} */
    public static J4pClientBuilder password(String pPassword) {
        return new J4pClientBuilder().password(pPassword);
    }

    /** See {@link J4pClientBuilder#singleConnection()} */
    public static J4pClientBuilder singleConnection() {
        return new J4pClientBuilder().singleConnection();
    }

    /** See {@link J4pClientBuilder#pooledConnections()} */
    public static J4pClientBuilder pooledConnections() {
        return new J4pClientBuilder().pooledConnections();
    }

    /** See {@link J4pClientBuilder#connectionTimeout(int)} */
    public static J4pClientBuilder connectionTimeout(int pTimeOut) {
        return new J4pClientBuilder().connectionTimeout(pTimeOut);
    }

    /** See {@link J4pClientBuilder#socketTimeout(int)} */
    public static J4pClientBuilder socketTimeout(int pTimeOut) {
        return new J4pClientBuilder().socketTimeout(pTimeOut);
    }

    /** See {@link J4pClientBuilder#maxTotalConnections(int)} */
    public static J4pClientBuilder maxTotalConnections(int pConnections) {
        return new J4pClientBuilder().maxTotalConnections(pConnections);
    }

    /** See {@link J4pClientBuilder#defaultMaxConnectionsPerRoute(int)} */
    public static J4pClientBuilder defaultMaxConnectionsPerRoute(int pConnectionsPerRoute) {
        return new J4pClientBuilder().defaultMaxConnectionsPerRoute(pConnectionsPerRoute);
    }

    /** See {@link J4pClientBuilder#maxConnectionPoolTimeout(int)} */
    public static J4pClientBuilder maxConnectionPoolTimeout(int pConnectionPoolTimeout) {
        return new J4pClientBuilder().maxConnectionPoolTimeout(pConnectionPoolTimeout);
    }

    /** See {@link J4pClientBuilder#contentCharset(String)} */
    public static J4pClientBuilder contentCharset(String pContentCharset) {
        return new J4pClientBuilder().contentCharset(pContentCharset);
    }

    /** See {@link J4pClientBuilder#expectContinue(boolean)} */
    public static J4pClientBuilder expectContinue(boolean pUse) {
        return new J4pClientBuilder().expectContinue(pUse);
    }

    /** See {@link J4pClientBuilder#tcpNoDelay(boolean)} */
    public static J4pClientBuilder tcpNoDelay(boolean pUse) {
        return new J4pClientBuilder().tcpNoDelay(pUse);
    }

    /** See {@link J4pClientBuilder#socketBufferSize(int)} */
    public static J4pClientBuilder socketBufferSize(int pSize) {
        return new J4pClientBuilder().socketBufferSize(pSize);
    }

    /** See {@link J4pClientBuilder#cookieStore(CookieStore)} */
    public static J4pClientBuilder cookieStore(CookieStore pStore) {
        return new J4pClientBuilder().cookieStore(pStore);
    }

    /** See {@link J4pClientBuilder#authenticator(J4pAuthenticator)} */
    public static J4pClientBuilder authenticator(J4pAuthenticator pAuthenticator) {
        return new J4pClientBuilder().authenticator(pAuthenticator);
    }

    public static J4pClientBuilder responseExtractor(J4pResponseExtractor pExtractor) {
        return new J4pClientBuilder().responseExtractor(pExtractor);
    }
}
