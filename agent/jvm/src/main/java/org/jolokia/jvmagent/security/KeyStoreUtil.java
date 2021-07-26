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
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.*;
import java.util.Collection;
import java.util.Date;

import org.jolokia.Version;
import org.jolokia.jvmagent.security.asn1.DERBitString;
import org.jolokia.jvmagent.security.asn1.DERDirect;
import org.jolokia.jvmagent.security.asn1.DERInteger;
import org.jolokia.jvmagent.security.asn1.DERNull;
import org.jolokia.jvmagent.security.asn1.DERObject;
import org.jolokia.jvmagent.security.asn1.DERObjectIdentifier;
import org.jolokia.jvmagent.security.asn1.DEROctetString;
import org.jolokia.jvmagent.security.asn1.DERSequence;
import org.jolokia.jvmagent.security.asn1.DERSet;
import org.jolokia.jvmagent.security.asn1.DERTaggedObject;
import org.jolokia.jvmagent.security.asn1.DERUtcTime;
import org.jolokia.util.Base64Util;

/**
 * Utility class for handling keystores
 *
 * @author roland
 * @since 30/09/15
 */
public class KeyStoreUtil {

    private KeyStoreUtil() {
    }

    /**
     * Update a keystore with a CA certificate
     *
     * @param pTrustStore the keystore to update
     * @param pCaCert     CA cert as PEM used for the trust store
     */
    public static void updateWithCaPem(KeyStore pTrustStore, File pCaCert)
            throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        InputStream is = new FileInputStream(pCaCert);
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            Collection<? extends Certificate> certificates = certFactory.generateCertificates(is);

            for (Certificate c : certificates) {
                X509Certificate cert = (X509Certificate) c;
                String alias = cert.getSubjectX500Principal().getName();
                pTrustStore.setCertificateEntry(alias, cert);
            }
        } finally {
            is.close();
        }
    }

    /**
     * Update a key store with the keys found in a server PEM and its key file.
     *
     * @param pKeyStore   keystore to update
     * @param pServerCert server certificate
     * @param pServerKey  server key
     * @param pKeyAlgo    algorithm used in the keystore (e.g. "RSA")
     * @param pPassword   password to use for the key file. must not be null, use <code>char[0]</code>
     *                    for an empty password.
     */
    public static void updateWithServerPems(KeyStore pKeyStore, File pServerCert, File pServerKey, String pKeyAlgo, char[] pPassword)
            throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException {
        InputStream is = new FileInputStream(pServerCert);
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(is);

            byte[] keyBytes = decodePem(pServerKey);
            PrivateKey privateKey;

            KeyFactory keyFactory = KeyFactory.getInstance(pKeyAlgo);
            try {
                // First let's try PKCS8
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } catch (InvalidKeySpecException e) {
                // Otherwise try PKCS1
                RSAPrivateCrtKeySpec keySpec = PKCS1Util.decodePKCS1(keyBytes);
                privateKey = keyFactory.generatePrivate(keySpec);
            }

            String alias = cert.getSubjectX500Principal().getName();
            pKeyStore.setKeyEntry(alias, privateKey, pPassword, new Certificate[]{cert});
        } finally {
            is.close();
        }
    }

    /**
     * Update the given keystore with a self signed server certificate. This can be used if no
     * server certificate is provided from the outside and no SSL verification is used by the client.
     *
     * @param pKeyStore keystore to update
     */
    public static void updateWithSelfSignedServerCertificate(KeyStore pKeyStore)
            throws NoSuchAlgorithmException, KeyStoreException {

        final String[] certAttributes = { "Jolokia Agent " + Version.getAgentVersion(), // CN
                                          "JVM",                                        // OU
                                          "jolokia.org",                                // O
                                          "Pegnitz",                                    // L
                                          "Franconia",                                  // ST
                                          "DE" };

        // Need to do it via reflection because Java8 moved class to a different package
        KeyPair keypair = createKeyPair();
        PrivateKey privKey = keypair.getPrivate();

        X509Certificate[] chain = new X509Certificate[1];
        chain[0] = getSelfCertificate(keypair, certAttributes, new Date(), (long) 3650 * 24 * 60 * 60);
        pKeyStore.setKeyEntry("jolokia-agent", privKey, new char[0], chain);
    }

    // =============================================================================================
    // Reflection based access to KeyGen classes:

    private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static X509Certificate getSelfCertificate(KeyPair keypair, String[] attributes, Date date, long l) {
        // https://datatracker.ietf.org/doc/html/rfc5280#section-4.1:
        // TBSCertificate  ::=  SEQUENCE  {
        //      version         [0]  EXPLICIT Version DEFAULT v1,
        //      serialNumber         CertificateSerialNumber,
        //      signature            AlgorithmIdentifier,
        //      issuer               Name,
        //      validity             Validity,
        //      subject              Name,
        //      subjectPublicKeyInfo SubjectPublicKeyInfo,
        //      issuerUniqueID  [1]  IMPLICIT UniqueIdentifier OPTIONAL,
        //                           -- If present, version MUST be v2 or v3
        //      subjectUniqueID [2]  IMPLICIT UniqueIdentifier OPTIONAL,
        //                           -- If present, version MUST be v2 or v3
        //      extensions      [3]  EXPLICIT Extensions OPTIONAL
        //                           -- If present, version MUST be v3
        //      }
        // Version  ::=  INTEGER  {  v1(0), v2(1), v3(2)  }
        //
        // CertificateSerialNumber  ::=  INTEGER
        //
        // Validity ::= SEQUENCE {
        //      notBefore      Time,
        //      notAfter       Time }
        //
        // Time ::= CHOICE {
        //      utcTime        UTCTime,
        //      generalTime    GeneralizedTime }
        //
        // UniqueIdentifier  ::=  BIT STRING
        //
        // SubjectPublicKeyInfo  ::=  SEQUENCE  {
        //      algorithm            AlgorithmIdentifier,
        //      subjectPublicKey     BIT STRING  }
        //
        // Extensions  ::=  SEQUENCE SIZE (1..MAX) OF Extension
        //
        // Extension  ::=  SEQUENCE  {
        //      extnID      OBJECT IDENTIFIER,
        //      critical    BOOLEAN DEFAULT FALSE,
        //      extnValue   OCTET STRING
        //                  -- contains the DER encoding of an ASN.1 value
        //                  -- corresponding to the extension type identified
        //                  -- by extnID
        //      }

        DERTaggedObject version = new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, false, 0, new DERInteger(2));
        DERInteger serialNumber = new DERInteger(0x051386F6);
        DERSequence signature = new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_sha1WithRSAEncryption),
                new DERNull()
        });
        DERSequence issuerAndSubject = new DERSequence(new DERObject[] {
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_countryName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[5])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_stateOrProvinceName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[4])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_localityName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[3])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_organizationName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[2])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_organizationalUnitName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[1])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_commonName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[0])
                }) })
        });
        DERSequence validity = new DERSequence(new DERObject[] {
                new DERUtcTime(date),
                new DERUtcTime(new Date(date.getTime() + l))
        });

        DERSequence tbsCertificate = new DERSequence(new DERObject[] {
                version,
                serialNumber,
                signature,
                issuerAndSubject,
                validity,
                issuerAndSubject,
                new DERDirect(keypair.getPublic().getEncoded())
        });

        try {
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initSign(keypair.getPrivate(), SecureRandom.getInstance("SHA1PRNG"));
            sig.update(tbsCertificate.getEncoded());
            byte[] signatureBytes = sig.sign();

            DERSequence certificate = new DERSequence(new DERObject[] {
                    tbsCertificate,
                    new DERSequence(new DERObject[] {
                            new DERObjectIdentifier(DERObjectIdentifier.OID_sha1WithRSAEncryption),
                            new DERNull()
                    }),
                    new DERBitString(signatureBytes)
            });

            CertificateFactory cf = CertificateFactory.getInstance("X509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
        } catch (final InvalidKeyException e) {
            throw new IllegalStateException("The getSelfCertificate-method threw an error.", e);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("The getSelfCertificate-method threw an error.", e);
        } catch (final SignatureException e) {
            throw new IllegalStateException("The getSelfCertificate-method threw an error.", e);
        } catch (final CertificateException e) {
            throw new IllegalStateException("The getSelfCertificate-method threw an error.", e);
        }
    }

    // This method is inspired and partly taken over from
    // http://oauth.googlecode.com/svn/code/java/
    // All credits to belong to them.
    private static byte[] decodePem(File pemFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(pemFile));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("-----BEGIN ")) {
                    return readBytes(pemFile, reader, line.trim().replace("BEGIN", "END"));
                }
            }
            throw new IOException("PEM " + pemFile + " is invalid: no begin marker");
        } finally {
            reader.close();
        }
    }

    private static byte[] readBytes(File pemFile, BufferedReader reader, String endMarker) throws IOException {
        String line;
        StringBuffer buf = new StringBuffer();

        while ((line = reader.readLine()) != null) {
            if (line.indexOf(endMarker) != -1) {
                return Base64Util.decode(buf.toString());
            }
            buf.append(line.trim());
        }
        throw new IOException(pemFile + " is invalid : No end marker");
    }
}

