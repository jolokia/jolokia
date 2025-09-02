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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jolokia.server.core.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 01/10/15
 */
public class KeyStoreUtilTest {

    public static final String CA_CERT_SUBJECT_DN_CN = "CN=ca.test.jolokia.org";
    public static final String SERVER_CERT_SUBJECT_DN = "C=DE, ST=Franconia, L=Pegnitz, OU=Test, O=jolokia.org, CN=Server Cert signed and with extended key usage server";

    public static final String CA_ALIAS = "1.2.840.113549.1.9.1=#1612726f6c616e64406a6f6c6f6b69612e6f7267,C=DE,ST=Bavaria,L=Pegnitz,OU=Dev,O=Jolokia,CN=ca.test.jolokia.org|52247990346977554835626411219862801398774599212";
    public static final String SERVER_ALIAS = "c=de,st=franconia,l=pegnitz,ou=test,o=jolokia.org,cn=server cert signed and with extended key usage server";

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
        assertEquals(aliases.size(), 3);
        Map<String, String> expectedAliases = new HashMap<>();
        expectedAliases.put("1.2.840.113549.1.9.1=#1612726f6c616e64406a6f6c6f6b69612e6f7267,c=de,st=bavaria,l=pegnitz,ou=dev,o=jolokia,cn=ca.test.jolokia.org|52247990346977554835626411219862801398774599212", "ca.test.jolokia.org");
        expectedAliases.put("cn=another.test.jolokia.org,ou=jolokia,o=jolokia,l=pegnitz,st=bavaria,c=us|167767600", "another.test.jolokia.org");
        expectedAliases.put("cn=another.test.jolokia.org,ou=jolokia,o=jolokia,l=pegnitz,st=bavaria,c=us|42", "another.test.jolokia.org");

        for (String alias : aliases) {

            assertNotNull(alias);
            String expectedSubjectDN = expectedAliases.remove(alias);
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
    public void testKeyStoreWithCertChain() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, UnrecoverableKeyException {
        File serverPem = getTempFile("server/server-chain.pem");
        File keyPem = getTempFile("server/key.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithServerPems(keystore, serverPem, keyPem, "RSA", new char[0]);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("server"));

        Certificate[] chain = keystore.getCertificateChain(alias);
        assertEquals(chain.length, 3);

        String[] expectedSubjectDNs = new String[]{
            "CN=Server Cert signed and with extended key usage server, C=DE, ST=Franconia, L=Pegnitz, OU=Test, O=jolokia.org",
            "CN=Intermediate CA, OU=Test, O=jolokia.org, L=Mountain View, ST=California, C=US",
            "CN=Root CA, OU=Test, O=jolokia.org, L=Mountain View, ST=California, C=US"
        };

        for (int i = 0; i < expectedSubjectDNs.length; i++) {
            assertEquals(((X509Certificate) chain[i]).getSubjectDN().getName(), expectedSubjectDNs[i]);
            RSAPublicKey pubKey = (RSAPublicKey) chain[i].getPublicKey();
            assertEquals("RSA", pubKey.getAlgorithm());
        }

        X509Certificate serverCert = (X509Certificate) chain[0];
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keystore.getKey(alias, new char[0]);
        assertEquals("RSA", key.getAlgorithm());
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
    public void testInvalid() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {

        for (String file : new String[]{"invalid/base64.pem", "invalid/begin.pem", "invalid/end.pem"}) {
            File invalidPem = getTempFile(file);

            KeyStore keystore = createKeyStore();
            try {
                KeyStoreUtil.updateWithCaPem(keystore, invalidPem);
                fail();
            } catch (Exception ignored) {
            }
            try {
                KeyStoreUtil.updateWithServerPems(keystore, getTempFile("server/cert.pem"), invalidPem, "RSA", new char[0]);
                fail();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void testSelfSignedCertificate() throws Exception {
        KeyStore keystore = createKeyStore();
        long millis = System.currentTimeMillis();
        updateKeyStoreWithSelfSignedCert(keystore);
        System.out.printf("SelfSigned Cert: Duration = %d ms%n", System.currentTimeMillis() - millis);
    }

    private void updateKeyStoreWithSelfSignedCert(KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException {
        KeyStoreUtil.updateWithSelfSignedServerCertificate(keystore, null);
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
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(Objects.requireNonNull(is))); FileWriter writer = new FileWriter(dest)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
            return dest;
        }
    }

    private List<String> asList(Enumeration<String> enumeration) {
        List<String> ret = new ArrayList<>();
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
