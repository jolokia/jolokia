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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.jolokia.core.util.CryptoUtil;
import org.jolokia.server.core.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public static final Logger LOG = LoggerFactory.getLogger(KeyStoreUtilTest.class);

    public static final String CA_CERT_SUBJECT_DN_CN = "CN=ca.test.jolokia.org";
    public static final String SERVER_CERT_SUBJECT_DN = "C=DE,ST=Franconia,L=Pegnitz,OU=Test,O=jolokia.org,CN=Server Cert signed and with extended key usage server";

    public static final String CA_ALIAS = "1.2.840.113549.1.9.1=#1612726f6c616e64406a6f6c6f6b69612e6f7267,C=DE,ST=Bavaria,L=Pegnitz,OU=Dev,O=Jolokia,CN=ca.test.jolokia.org|52247990346977554835626411219862801398774599212";
    public static final String SERVER_ALIAS = "c=de,st=franconia,l=pegnitz,ou=test,o=jolokia.org,cn=server cert signed and with extended key usage server";

    @Test
    public void keyFactories() throws Exception {
        // Temurin-17.0.17+10
        // DSA - sun.security.provider.DSAKeyFactory
        // RSA - sun.security.rsa.RSAKeyFactory$Legacy
        // RSASSA-PSS, PSS - sun.security.rsa.RSAKeyFactory$PSS
        // EC, EllipticCurve - sun.security.ec.ECKeyFactory
        // Ed25519 - sun.security.ec.ed.EdDSAKeyFactory.Ed25519
        // Ed448 - sun.security.ec.ed.EdDSAKeyFactory.Ed448
        // EdDSA - sun.security.ec.ed.EdDSAKeyFactory
        // X25519 - sun.security.ec.XDHKeyFactory.X25519
        // X448 - sun.security.ec.XDHKeyFactory.X448
        // XDH - sun.security.ec.XDHKeyFactory
        // DiffieHellman - com.sun.crypto.provider.DHKeyFactory
        assertNotNull(KeyFactory.getInstance("DSA"));
        assertNotNull(KeyFactory.getInstance("RSA"));
        assertNotNull(KeyFactory.getInstance("RSASSA-PSS"));
        assertNotNull(KeyFactory.getInstance("EC"));
        assertNotNull(KeyFactory.getInstance("Ed25519"));
        assertNotNull(KeyFactory.getInstance("Ed448"));
        assertNotNull(KeyFactory.getInstance("EdDSA"));
        assertNotNull(KeyFactory.getInstance("X25519"));
        assertNotNull(KeyFactory.getInstance("X448"));
        assertNotNull(KeyFactory.getInstance("XDH"));
        assertNotNull(KeyFactory.getInstance("DiffieHellman"));
    }

    @Test
    public void keyGenerators() throws Exception {
        // Temurin-17.0.17+10
        // DSA - sun.security.provider.DSAKeyPairGenerator$Current
        // RSA - sun.security.rsa.RSAKeyPairGenerator$Legacy
        // RSASSA-PSS, PSS - sun.security.rsa.RSAKeyPairGenerator$PSS
        // EC, EllipticCurve : sun.security.ec.ECKeyPairGenerator
        // Ed25519 - sun.security.ec.ed.EdDSAKeyPairGenerator.Ed25519
        // Ed448 - sun.security.ec.ed.EdDSAKeyPairGenerator.Ed448
        // EdDSA - sun.security.ec.ed.EdDSAKeyPairGenerator
        // X25519 - sun.security.ec.XDHKeyPairGenerator.X25519
        // X448 - sun.security.ec.XDHKeyPairGenerator.X448
        // XDH - sun.security.ec.XDHKeyPairGenerator
        // DiffieHellman - com.sun.crypto.provider.DHKeyPairGenerator
        assertNotNull(KeyPairGenerator.getInstance("DSA"));
        assertNotNull(KeyPairGenerator.getInstance("RSA"));
        assertNotNull(KeyPairGenerator.getInstance("RSASSA-PSS"));
        assertNotNull(KeyPairGenerator.getInstance("EC"));
        assertNotNull(KeyPairGenerator.getInstance("Ed25519"));
        assertNotNull(KeyPairGenerator.getInstance("Ed448"));
        assertNotNull(KeyPairGenerator.getInstance("EdDSA"));
        assertNotNull(KeyPairGenerator.getInstance("X25519"));
        assertNotNull(KeyPairGenerator.getInstance("X448"));
        assertNotNull(KeyPairGenerator.getInstance("XDH"));
        assertNotNull(KeyPairGenerator.getInstance("DiffieHellman"));
    }

    @Test
    public void readingEncryptedKeys() throws Exception {
        CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File("target/keys/rsa_enc.pem"));
        byte[] data = cryptoData.derData();
        EncryptedPrivateKeyInfo info = new EncryptedPrivateKeyInfo(data);
        System.out.println(info.getAlgName());
        System.out.println(info.getAlgParameters());

        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(info.getAlgParameters().toString()); // !
        SecretKey key = keyFactory.generateSecret(new PBEKeySpec("mysecret".toCharArray()));
        Cipher cipher = Cipher.getInstance(info.getAlgParameters().toString()); // !
        cipher.init(Cipher.DECRYPT_MODE, key, info.getAlgParameters());
        PKCS8EncodedKeySpec pkcs8 = info.getKeySpec(cipher);

        PrivateKey pkey = KeyFactory.getInstance(pkcs8.getAlgorithm()).generatePrivate(pkcs8);
        System.out.println(pkey);
    }

    @Test
    public void testTrustStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        File caPem = getTempFile("ca/cert.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithCaCertificates(keystore, caPem);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("ca.test.jolokia.org"));
        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
        assertTrue(cert.getSubjectX500Principal().getName().contains(CA_CERT_SUBJECT_DN_CN));
        RSAPublicKey key = (RSAPublicKey) cert.getPublicKey();
        assertEquals(key.getAlgorithm(),"RSA");
    }

    @Test
    public void testTrustStoreWithMultipleEntries() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        File caPem = getTempFile("ca/cert-multi.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithCaCertificates(keystore, caPem);

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
            assertTrue(cert.getSubjectX500Principal().getName().contains(expectedSubjectDN));
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

        KeyStoreUtil.updateWithServerCertificate(keystore, serverPem, keyPem, "RSA", new char[0]);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("server"));

        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
        assertEquals(cert.getSubjectX500Principal().getName(), SERVER_CERT_SUBJECT_DN);
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keystore.getKey(alias, new char[0]);
        assertEquals(key.getAlgorithm(), "RSA");
        RSAPublicKey pubKey = (RSAPublicKey) cert.getPublicKey();
        assertEquals(pubKey.getAlgorithm(), "RSA");
    }

    @Test
    public void testKeyStoreWithDerEncodedItems() throws Exception {
        File serverPem = getTempFile("server/cert.der");
        File keyPem = getTempFile("server/key.der");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithServerCertificate(keystore, serverPem, keyPem, "RSA", new char[0]);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("server"));

        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
        assertEquals(cert.getSubjectX500Principal().getName(), SERVER_CERT_SUBJECT_DN);
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keystore.getKey(alias, new char[0]);
        assertEquals(key.getAlgorithm(), "RSA");
        RSAPublicKey pubKey = (RSAPublicKey) cert.getPublicKey();
        assertEquals(pubKey.getAlgorithm(), "RSA");
    }

    @Test
    public void testKeyStoreWithCertChain() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, UnrecoverableKeyException {
        File serverPem = getTempFile("server/server-chain.pem");
        File keyPem = getTempFile("server/key.pem");
        KeyStore keystore = createKeyStore();

        KeyStoreUtil.updateWithServerCertificate(keystore, serverPem, keyPem, "RSA", new char[0]);

        List<String> aliases = asList(keystore.aliases());
        assertEquals(aliases.size(), 1);
        String alias = aliases.get(0);
        assertTrue(alias.contains("server"));

        Certificate[] chain = keystore.getCertificateChain(alias);
        assertEquals(chain.length, 3);

        String[] expectedSubjectDNs = new String[]{
            "CN=Server Cert signed and with extended key usage server,C=DE,ST=Franconia,L=Pegnitz,OU=Test,O=jolokia.org",
            "CN=Intermediate CA,OU=Test,O=jolokia.org,L=Mountain View,ST=California,C=US",
            "CN=Root CA,OU=Test,O=jolokia.org,L=Mountain View,ST=California,C=US"
        };

        for (int i = 0; i < expectedSubjectDNs.length; i++) {
            assertEquals(((X509Certificate) chain[i]).getSubjectX500Principal().getName(), expectedSubjectDNs[i]);
            RSAPublicKey pubKey = (RSAPublicKey) chain[i].getPublicKey();
            assertEquals(pubKey.getAlgorithm(), "RSA");
        }

        X509Certificate serverCert = (X509Certificate) chain[0];
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keystore.getKey(alias, new char[0]);
        assertEquals(key.getAlgorithm(), "RSA");
    }

    @Test
    public void testBoth() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, InvalidKeySpecException, InvalidKeyException, NoSuchProviderException, SignatureException {
        File caPem = getTempFile("ca/cert.pem");
        File serverPem = getTempFile("server/cert.pem");
        File keyPem = getTempFile("server/key.pem");

        KeyStore keystore = createKeyStore();
        KeyStoreUtil.updateWithCaCertificates(keystore, caPem);
        KeyStoreUtil.updateWithServerCertificate(keystore, serverPem, keyPem, "RSA", new char[0]);

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
                KeyStoreUtil.updateWithCaCertificates(keystore, invalidPem);
                fail();
            } catch (Exception ignored) {
            }
            try {
                KeyStoreUtil.updateWithServerCertificate(keystore, getTempFile("server/cert.pem"), invalidPem, "RSA", new char[0]);
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
        assertEquals(cert.getSubjectX500Principal().getName(), "CN=Jolokia Agent " + Version.getAgentVersion() + ",OU=JVM,O=jolokia.org,L=Pegnitz,ST=Franconia,C=DE");
        assertEquals(cert.getSubjectX500Principal(), cert.getIssuerX500Principal());
    }

    // ========================================================

    private KeyStore createKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        return keystore;
    }

    private File getTempFile(String path) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream("/certs/" + path)) {
            File dest = File.createTempFile("cert-", "pem");
            if (is != null) {
                Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
