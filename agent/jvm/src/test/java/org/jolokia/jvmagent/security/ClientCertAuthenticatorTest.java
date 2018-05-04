package org.jolokia.jvmagent.security;
/*
 *
 * Copyright 2016 Roland Huss
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

import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import com.sun.net.httpserver.*;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 06/07/16
 */
public class ClientCertAuthenticatorTest extends BaseAuthenticatorTest {

    @Test
    public void positive() throws Exception {
        ClientCertAuthenticator auth = new ClientCertAuthenticator(getConfig("clientPrincipal","cn=roland",
                                                                             "extendedClientCheck","true"));
        Authenticator.Result result = auth.authenticate(createHttpsExchange(new Headers(),"uid=blub,cn=roland,o=redhat,c=de", true, true));
        assertTrue(result instanceof Authenticator.Success);
    }

    @Test
    public void positiveWithoutExtendedClientCheck() throws Exception {
        ClientCertAuthenticator auth = new ClientCertAuthenticator(getConfig("clientPrincipal","cn=roland",
                                                                             "extendedClientCheck","false"));
        Authenticator.Result result = auth.authenticate(createHttpsExchange(new Headers(),"uid=blub,cn=roland,o=redhat,c=de", true, false));
        assertTrue(result instanceof Authenticator.Success);
    }

    @Test
    public void negativeWithExtendedClientCheck() throws Exception {
        ClientCertAuthenticator auth = new ClientCertAuthenticator(getConfig("clientPrincipal","cn=roland",
                                                                             "extendedClientCheck","true"));
        Authenticator.Result result = auth.authenticate(createHttpsExchange(new Headers(),"uid=blub,cn=roland,o=redhat,c=de", true, false));
        assertTrue(result instanceof Authenticator.Failure);
    }

    @Test
    public void negativeWithWrongClientPrincipal() throws Exception {
        ClientCertAuthenticator auth = new ClientCertAuthenticator(getConfig("clientPrincipal","cn=roland",
                                                                             "extendedClientCheck","true"));
        Authenticator.Result result = auth.authenticate(createHttpsExchange(new Headers(),"uid=blub,cn=manuel,o=redhat,c=de", true, true));
        assertTrue(result instanceof Authenticator.Failure);
    }

    @Test
    public void negativeWithNoCert() throws Exception {
                ClientCertAuthenticator auth = new ClientCertAuthenticator(getConfig("clientPrincipal","cn=roland",
                                                                             "extendedClientCheck","false"));
        Authenticator.Result result = auth.authenticate(createHttpsExchange(new Headers(),"uid=blub,cn=roland,o=redhat,c=de",
                                                                            false /* no cert */, false));
        assertTrue(result instanceof Authenticator.Failure);
    }


    @Test
    public void wrongExchange() throws Exception {
        ClientCertAuthenticator auth = new ClientCertAuthenticator(getConfig());
        Authenticator.Result result = auth.authenticate(createHttpExchange(new Headers()));
        assertTrue(result instanceof Authenticator.Failure);
    }

    private JolokiaServerConfig getConfig(String ... opts) {
        Map<String, String> config = new HashMap<String, String>();
        for (int i=0; i < opts.length; i +=2) {
            config.put(opts[i],opts[i+1]);
        }
        return new JolokiaServerConfig(config);
    }

    private HttpExchange createHttpsExchange(Headers respHeaders, String principalName,
                                             boolean withCert,
                                             boolean withExtendedUsage, String... reqHeaderValues)
        throws SSLPeerUnverifiedException, CertificateParsingException {
        HttpsExchange ex = createMock(HttpsExchange.class);
        Headers reqHeaders = new Headers();
        for (int i = 0; i < reqHeaderValues.length; i+=2) {
            reqHeaders.put(reqHeaderValues[i], Arrays.asList(reqHeaderValues[i + 1]));
        }
        expect(ex.getResponseHeaders()).andStubReturn(respHeaders);
        expect(ex.getRequestHeaders()).andStubReturn(reqHeaders);
        // For JDK6:
        expect(ex.getHttpContext()).andStubReturn(null);

        // SSLSession
        SSLSession session = createMock(SSLSession.class);
        expect(ex.getSSLSession()).andStubReturn(session);
        if (withCert) {
            X509Certificate cert = createMock(X509Certificate.class);
            Certificate[] certs = new Certificate[]{
                cert
            };
            expect(session.getPeerCertificates()).andStubReturn(certs);
            expect(cert.getExtendedKeyUsage()).andStubReturn(Arrays.asList(withExtendedUsage ? ClientCertAuthenticator.CLIENTAUTH_OID : ""));
            replay(cert);
        } else {
            expect(session.getPeerCertificates()).andStubReturn(null);
        }

        // Principal
        X500Principal principal = new X500Principal(principalName);
        expect(session.getPeerPrincipal()).andStubReturn(principal);

        replay(ex, session);
        return ex;
    }
}
