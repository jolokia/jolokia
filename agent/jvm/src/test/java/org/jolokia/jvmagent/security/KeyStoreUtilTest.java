package org.jolokia.jvmagent.security;/*
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

import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jolokia.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 01/10/15
 */
public class KeyStoreUtilTest {

    public static final String CA_CERT_SUBJECT_DN_CN = "CN=ca.test.jolokia.org";
    public static final String SERVER_CERT_SUBJECT_DN = "CN=Server Cert signed and with extended key usage server, C=DE, ST=Franconia, L=Pegnitz, OU=Test, O=jolokia.org";

    public static final String CA_ALIAS = "cn=ca.test.jolokia.org,c=de,st=bavaria,l=pegnitz,1.2.840.113549.1.9.1=#1612726f6c616e64406a6f6c6f6b69612e6f7267,ou=dev,o=jolokia";
    public static final String SERVER_ALIAS = "cn=server cert signed and with extended key usage server,c=de,st=franconia,l=pegnitz,ou=test,o=jolokia.org";

    @Test
    public void testTrustStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        File caPem = getTempFile("ca/cert.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithCaPem(keystore, caPem);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("ca.test.jolokia.org"));
        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
        assertTrue(cert.getSubjectDN().getName().contains(CA_CERT_SUBJECT_DN_CN));
        RSAPublicKey key = (RSAPublicKey) cert.getPublicKey();
        assertEquals(key.getAlgorithm(),"RSA");
    }

    @Test
    public void testTrustStoreWithMultipleEntries() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        File caPem = getTempFile("ca/cert-multi.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithCaPem(keystore, caPem);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 2);
        Map<String, String> expectedAliases = new HashMap<String, String>();
        expectedAliases.put("ca.test.jolokia.org",CA_CERT_SUBJECT_DN_CN);
        expectedAliases.put("another.test.jolokia.org","CN=another.test.jolokia.org");

        for (String alias : aliases) {

            String key = findMatchingKeyAsSubstring(expectedAliases, alias);
            assertNotNull(key);
            String expectedSubjectDN = expectedAliases.remove(key);
            assertNotNull(expectedSubjectDN);

            X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
            assertTrue(cert.getSubjectDN().getName().contains(expectedSubjectDN));
            RSAPublicKey certPublicKey = (RSAPublicKey) cert.getPublicKey();
            assertEquals(certPublicKey.getAlgorithm(),"RSA");
        }
        assertEquals(expectedAliases.size(),0);
    }

    @Test
    public void testKeyStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, UnrecoverableKeyException {
        File serverPem = getTempFile("server/cert.pem");
        File keyPem = getTempFile("server/key.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithServerPems(keystore, serverPem, keyPem, "RSA", new char[0]);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("server"));

        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
        assertEquals(cert.getSubjectDN().getName(), SERVER_CERT_SUBJECT_DN);
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keystore.getKey(alias, new char[0]);
        assertEquals("RSA", key.getAlgorithm());
        RSAPublicKey pubKey = (RSAPublicKey) cert.getPublicKey();
        assertEquals("RSA", pubKey.getAlgorithm());
    }

    @Test
    public void testBoth() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, InvalidKeySpecException, InvalidKeyException, NoSuchProviderException, SignatureException {
        File caPem = getTempFile("ca/cert.pem");
        File serverPem = getTempFile("server/cert.pem");
        File keyPem = getTempFile("server/key.pem");

        KeyStore keystore = createKeyStore();
        KeyStoreUtil.updateWithCaPem(keystore, caPem);
        KeyStoreUtil.updateWithServerPems(keystore, serverPem, keyPem, "RSA", new char[0]);

        X509Certificate caCert = (X509Certificate) keystore.getCertificate(CA_ALIAS);
        X509Certificate serverCert = (X509Certificate) keystore.getCertificate(SERVER_ALIAS);

        // Check that server cert is signed by ca
        serverCert.verify(caCert.getPublicKey());
    }

    @Test
    public void testInvalid() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException {

        for (String file : new String[]{"invalid/base64.pem", "invalid/begin.pem", "invalid/end.pem"}) {
            File invalidPem = getTempFile(file);

            KeyStore keystore = createKeyStore();
            try {
                KeyStoreUtil.updateWithCaPem(keystore, invalidPem);
                fail();
            } catch (Exception exp) {
            }
            try {
                KeyStoreUtil.updateWithServerPems(keystore, getTempFile("server/cert.pem"), invalidPem, "RSA", new char[0]);
                fail();
            } catch (Exception exp) {
            }
        }
    }

    @Test
    public void testSelfSignedCertificate() throws Exception {
        KeyStore keystore = createKeyStore();
        KeyStoreUtil.updateWithSelfSignedServerCertificate(keystore);
        X509Certificate cert = (X509Certificate) keystore.getCertificate("jolokia-agent");
        assertNotNull(cert);
        assertEquals(cert.getSubjectDN().getName(), "CN=Jolokia Agent " + Version.getAgentVersion() + ", OU=JVM, O=jolokia.org, L=Pegnitz, ST=Franconia, C=DE");
        assertEquals(cert.getSubjectDN(), cert.getIssuerDN());
    }

    // ========================================================

    private KeyStore createKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        return keystore;
    }

    private File getTempFile(String path) throws IOException {
        InputStream is = this.getClass().getResourceAsStream("/certs/" + path);
        File dest = File.createTempFile("cert-", "pem");
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(is));
        FileWriter writer = new FileWriter(dest);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
            return dest;
        } finally {
            writer.close();
            reader.close();
        }
    }

    private List<String> asList(Enumeration<String> enumeration) {
        List<String> ret = new ArrayList<String>();
        while (enumeration.hasMoreElements()) {
            ret.add(enumeration.nextElement());
        }
        return ret;
    }

    private String findMatchingKeyAsSubstring(Map<String, String> map, String fullValue) {
        for (String key : map.keySet()) {
            if (fullValue.contains(key)) {
                return key;
            }
        }
        return null;
    }

}
