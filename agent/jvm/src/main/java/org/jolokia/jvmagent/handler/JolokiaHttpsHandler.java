package org.jolokia.jvmagent.handler;/*
 * 
 * Copyright 2015 Roland Huss
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

import java.security.cert.*;
import java.util.*;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.server.core.service.api.JolokiaContext;

/**
 * Add specific HTTPs handling when https is used. This handler needs the full configuration
 * in order get to the SSL specific configuration.
 *
 * @author roland
 * @since 01/10/15
 */
public class JolokiaHttpsHandler extends JolokiaHttpHandler {

    // ASN.1 path to the extended usage info within a CERT
    private static final String CLIENTAUTH_OID = "1.3.6.1.5.5.7.3.2";

    // whether to use client cert authentication
    private final boolean useClientCertAuth;
    private final List<LdapName> allowedPrincipals;
    private final boolean extendedClientCheck;

    /**
     * Constructor
     *
     * @param pContext the Jolokia context to use
     */
    public JolokiaHttpsHandler(JolokiaContext pContext, JolokiaServerConfig pConfig) {
        super(pContext);
        useClientCertAuth = pConfig.useSslClientAuthentication();
        allowedPrincipals = parseAllowedPrincipals(pConfig);
        extendedClientCheck = pConfig.getExtendedClientCheck();
    }

    // =================================================================================

    // Verify https certs if its Https request and we have SSL auth enabled. Will be called before
    // handling the request
    protected void checkAuthentication(HttpExchange pHttpExchange) throws SecurityException {
        // Cast will always work since this handler is only used for Http
        HttpsExchange httpsExchange = (HttpsExchange) pHttpExchange;
        if (useClientCertAuth) {
            checkCertForClientUsage(httpsExchange);
            checkCertForAllowedPrincipals(httpsExchange);
        }
    }

    // Check the cert's principal against the list of given allowedPrincipals.
    // If no allowedPrincipals are given than every principal is allowed.
    // If an empty list as allowedPrincipals is given, no one is allowed to access
    private void checkCertForClientUsage(HttpsExchange pHttpsExchange) {
        try {
            Certificate[] peerCerts = pHttpsExchange.getSSLSession().getPeerCertificates();
            if (peerCerts != null && peerCerts.length > 0) {
                X509Certificate clientCert = (X509Certificate) peerCerts[0];

                // We required that the extended key usage must be present if we are using
                // client cert authentication
                if (extendedClientCheck &&
                    (clientCert.getExtendedKeyUsage() == null || !clientCert.getExtendedKeyUsage().contains(CLIENTAUTH_OID))) {
                    throw new SecurityException("No extended key usage available");
                }
            }
        } catch (ClassCastException e) {
            throw new SecurityException("No X509 client certificate");
        } catch (CertificateParsingException e) {
            throw new SecurityException("Can't parse client cert");
        } catch (SSLPeerUnverifiedException e) {
            throw new SecurityException("SSL Peer couldn't be verified");
        }
    }

    private void checkCertForAllowedPrincipals(HttpsExchange pHttpsExchange) {
        if (allowedPrincipals != null) {
            X500Principal certPrincipal;
            try {
                certPrincipal = (X500Principal) pHttpsExchange.getSSLSession().getPeerPrincipal();
                Set<Rdn> certPrincipalRdns = getPrincipalRdns(certPrincipal);
                for (LdapName principal : allowedPrincipals) {
                    for (Rdn rdn : principal.getRdns()) {
                        if (!certPrincipalRdns.contains(rdn)) {
                            throw new SecurityException("Principal " + certPrincipal + " not allowed");
                        }
                    }
                }
            } catch (SSLPeerUnverifiedException e) {
                throw new SecurityException("SSLPeer unverified");
            } catch (ClassCastException e) {
                throw new SecurityException("Internal: Invalid Principal class provided " + e);
            }
        }
    }

    private Set<Rdn> getPrincipalRdns(X500Principal principal) {
        try {
            LdapName certAsLdapName =new LdapName(principal.getName());
            return new HashSet<Rdn>(certAsLdapName.getRdns());
        } catch (InvalidNameException e) {
            throw new SecurityException("Cannot parse '" + principal + "' as LDAP name");
        }
    }

    private List<LdapName> parseAllowedPrincipals(JolokiaServerConfig pConfig) {
        List<String> principals = pConfig.getClientPrincipals();
        if (principals != null) {
            List<LdapName> ret = new ArrayList<LdapName>();
            for (String principal : principals) {
                try {
                    ret.add(new LdapName(principal));
                } catch (InvalidNameException e) {
                    throw new IllegalArgumentException("Principal '" + principal + "' cannot be parsed as X500 RDNs");
                }
            }
            return ret;
        } else {
            return null;
        }
    }
}
