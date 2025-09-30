package org.jolokia.client;

import org.jolokia.client.spi.J4pClientCustomizer;

/**
 * Authenticator using basic authentication.
 *
 * @author roland
 * @since 23.05.14
 */
public class BasicClientCustomizer implements J4pClientCustomizer {

    // Whether to always send the credentials
    private boolean preemptive = false;

    public BasicClientCustomizer() {
        this(false);
    }

    @Override
    public void configure(JolokiaClientBuilder builder) {
//        builder.cl
    }

    /**
     * Constructor for basic authentication
     *
     * @param pPreemptive whether to always send the authentication headers
     *                    (even if not asked for during a handshake)
     */
    public BasicClientCustomizer(boolean pPreemptive) {
        preemptive = pPreemptive;
    }

    /**
     * Alternative way for specifying preemptiveness
     * @return this authenticator for chaining
     */
    public J4pClientCustomizer preemptive() {
        preemptive = true;
        return this;
    }

}
