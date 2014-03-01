package org.jolokia.jvmagent;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

/**
 * HTTPS configurator
 * @author roland
 * @since 28.02.14
 */
final class JolokiaHttpsConfigurator extends HttpsConfigurator {

    private boolean useClientAuthentication;

    JolokiaHttpsConfigurator(SSLContext pSSLContext, boolean pUseClientAuthenication) {
        super(pSSLContext);
        useClientAuthentication = pUseClientAuthenication;
    }

    /** {@inheritDoc} */
    public void configure(HttpsParameters params) {
        try {
            // initialise the SSL context
            SSLContext context = SSLContext.getDefault();
            SSLEngine engine = context.createSSLEngine();
            params.setNeedClientAuth(useClientAuthentication);
            params.setCipherSuites(engine.getEnabledCipherSuites());
            params.setProtocols(engine.getEnabledProtocols());

            // get the default parameters
            params.setSSLParameters(context.getDefaultSSLParameters());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("jolokia: Exception while configuring SSL context: " + e,e);
        }
    }
}
