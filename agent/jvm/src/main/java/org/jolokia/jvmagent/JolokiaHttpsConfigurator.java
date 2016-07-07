package org.jolokia.jvmagent;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.*;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

/**
 * HTTPS configurator
 * @author roland
 * @since 28.02.14
 */
final class JolokiaHttpsConfigurator extends HttpsConfigurator {

    private final JolokiaServerConfig config;

    JolokiaHttpsConfigurator(SSLContext pSSLContext, JolokiaServerConfig pConfig) {
        super(pSSLContext);
        config = pConfig;
    }

    /** {@inheritDoc} */
    public void configure(HttpsParameters params) {
        try {
            // initialise the SSL context
            SSLContext context = SSLContext.getDefault();
            SSLEngine engine = context.createSSLEngine();
            // get the default parameters
            SSLParameters defaultSSLParameters = context.getDefaultSSLParameters();

            params.setNeedClientAuth(config.useSslClientAuthentication());
            defaultSSLParameters.setNeedClientAuth(config.useSslClientAuthentication());

            // Cipher Suites
            params.setCipherSuites(config.getSSLCipherSuites());
            defaultSSLParameters.setCipherSuites(config.getSSLCipherSuites());

            // Protocols
            params.setProtocols(config.getSSLProtocols());
            defaultSSLParameters.setProtocols(config.getSSLProtocols());

            params.setSSLParameters(defaultSSLParameters);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("jolokia: Exception while configuring SSL context: " + e,e);
        }
    }
}
