package org.jolokia.client;

import java.io.IOException;

import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

/**
 * Authenticator using basic authentication.
 *
 * @author roland
 * @since 23.05.14
 */
public class BasicAuthenticator implements J4pAuthenticator {

    // Whether to always send the credentials
    private boolean preemptive = false;

    public BasicAuthenticator() {
        this(false);
    }

    /**
     * Constructor for basic authentication
     *
     * @param pPreemptive whether to always send the authentication headers
     *                    (even if not asked for during a handshake)
     */
    public BasicAuthenticator(boolean pPreemptive) {
        preemptive = pPreemptive;
    }

    /**
     * Alternative way for specifying preemptiveness
     * @return this authenticator for chaining
     */
    public J4pAuthenticator preemptive() {
        preemptive = true;
        return this;
    }

    /** {@inheritDoc} */
    public void authenticate(HttpClientBuilder pBuilder, String pUser, String pPassword) {
        // Preparing the builder for the credentials
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(AuthScope.ANY),
                new UsernamePasswordCredentials(pUser, pPassword));
        pBuilder.setDefaultCredentialsProvider(credentialsProvider);
        if (preemptive) {
            pBuilder.addInterceptorFirst(new PreemptiveAuthInterceptor(new BasicScheme()));
        }
    }


    // =================================================================================================

    /**
     * Interceptor for preemptive, basic authentication authentication. Inspiration
     * taken from http://stackoverflow.com/a/3493746/207604
     *
     */
    static class PreemptiveAuthInterceptor implements HttpRequestInterceptor {

        // Auth scheme to use
        private AuthScheme authScheme;

        PreemptiveAuthInterceptor(AuthScheme pScheme) {
            authScheme = pScheme;
        }

        /** {@inheritDoc} */
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            if (authState.getAuthScheme() == null) {
                CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
                Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                if (creds == null) {
                    throw new HttpException("No credentials given for preemptive authentication");
                }
                authState.update(authScheme, creds);
            }
        }
    }
}
