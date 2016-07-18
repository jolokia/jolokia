/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.jvmagent.security;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpsExchange;
import org.jolokia.jvmagent.JolokiaServerConfig;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientCertAuthenticator extends Authenticator {

    // ASN.1 path to the extended usage info within a CERT
    static final String CLIENTAUTH_OID = "1.3.6.1.5.5.7.3.2";

    // whether to use client cert authentication
    private final boolean useSslClientAuthentication;
    private final List<LdapName> allowedPrincipals;
    private final boolean extendedClientCheck;

    /**
     * Constructor
     *
     * @param pConfig full server config (in contrast to the jolokia config use by the http-handler)
     */
    public ClientCertAuthenticator(JolokiaServerConfig pConfig) {
        useSslClientAuthentication = pConfig.useSslClientAuthentication();
        allowedPrincipals = parseAllowedPrincipals(pConfig);
        extendedClientCheck = pConfig.getExtendedClientCheck();
    }

    @Override
    public Result authenticate(HttpExchange httpExchange) {
        if( !(httpExchange instanceof HttpsExchange) ) {
            return new Failure(500);
        }
        try {
            HttpsExchange httpsExchange = (HttpsExchange) httpExchange;
            X509Certificate certificate = getClientCert(httpsExchange);
            if (certificate == null) {
                return new Failure(401);
            }
            checkCertForClientUsage(certificate);
            checkCertForAllowedPrincipals(httpsExchange);

            String name="";
            try {
                name = httpsExchange.getSSLSession().getPeerPrincipal().getName();
            } catch (SSLPeerUnverifiedException ignore) {
            }
            return new Success(new HttpPrincipal(name, "ssl"));

        } catch (SecurityException e) {
            return new Failure(403);
        }
    }

    // =================================================================================

    private X509Certificate getClientCert(HttpsExchange pHttpsExchange) {
        try {
            Certificate[] peerCerts = pHttpsExchange.getSSLSession().getPeerCertificates();
            return peerCerts != null && peerCerts.length > 0 ? (X509Certificate) peerCerts[0] : null;
        } catch (SSLPeerUnverifiedException e) {
            throw new SecurityException("SSL Peer couldn't be verified");
        }

    }

    // Check the cert's principal against the list of given allowedPrincipals.
    // If no allowedPrincipals are given than every principal is allowed.
    // If an empty list as allowedPrincipals is given, no one is allowed to access
    private void checkCertForClientUsage(X509Certificate clientCert) {
        try {
            // We required that the extended key usage must be present if we are using
            // client cert authentication
            if (extendedClientCheck &&
                (clientCert.getExtendedKeyUsage() == null ||
                 !clientCert.getExtendedKeyUsage().contains(CLIENTAUTH_OID))) {
                throw new SecurityException("No extended key usage available");
            }
        } catch (CertificateParsingException e) {
            throw new SecurityException("Can't parse client cert");
        }
    }

    private void checkCertForAllowedPrincipals(HttpsExchange pHttpsExchange) {
        if (allowedPrincipals != null) {
            X500Principal certPrincipal;
            try {
                certPrincipal = (X500Principal) pHttpsExchange.getSSLSession().getPeerPrincipal();
                Set<Rdn> certPrincipalRdns = getPrincipalRdns(certPrincipal);
                boolean matchFound = false;
                for (LdapName principal : allowedPrincipals) {
                    if (certPrincipalRdns.containsAll(principal.getRdns())) {
                        matchFound = true;
                        break;
                    }
                }
                if (!matchFound) {
                    throw new SecurityException("Principal " + certPrincipal + " not allowed");
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
