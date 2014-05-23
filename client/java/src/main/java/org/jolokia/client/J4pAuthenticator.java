package org.jolokia.client;

import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Authenticator which is called during construction and which can perform
 * a different login than Basic-Authentication against the plain JolokiaUrl
 *
 * @author roland
 * @since 23.05.14
 */
public interface J4pAuthenticator {

    /**
     * Hook called before the HTTP client has been build in order to prepare
     * for authentication
     *
     * @param pBuilder the HTTP client builder
     * @param pUser user to authenticate
     * @param pPassword her password
     */
    void authenticate(HttpClientBuilder pBuilder,String pUser, String pPassword);
}
