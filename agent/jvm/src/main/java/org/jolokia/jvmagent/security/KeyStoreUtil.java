/*
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
package org.jolokia.jvmagent.security;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jolokia.core.util.CryptoUtil;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.asn1.DERBitString;
import org.jolokia.asn1.DERBoolean;
import org.jolokia.asn1.DERDirect;
import org.jolokia.asn1.DERInteger;
import org.jolokia.asn1.DERNull;
import org.jolokia.asn1.DERObject;
import org.jolokia.asn1.DERObjectIdentifier;
import org.jolokia.asn1.DEROctetString;
import org.jolokia.asn1.DERSequence;
import org.jolokia.asn1.DERSet;
import org.jolokia.asn1.DERTaggedObject;
import org.jolokia.asn1.DERUtcTime;
import org.jolokia.server.core.Version;
import org.jolokia.server.core.util.NetworkUtil;

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
     * Update a keystore with a CA certificate (stored as <em>certificate entry</em>)
     *
     * @param pTrustStore the keystore to update
     * @param pCaCerts    CA certificate in PEM or DER format - may contain more entries (certificates) if in PEM format
     */
    public static void updateWithCaCertificates(KeyStore pTrustStore, File pCaCerts)
            throws IOException, CertificateException, KeyStoreException {
        try (InputStream is = new FileInputStream(pCaCerts)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            Collection<? extends Certificate> certificates = certFactory.generateCertificates(is);

            for (Certificate c : certificates) {
                X509Certificate cert = (X509Certificate) c;
                String alias = cert.getSubjectX500Principal().getName();
                String sid = cert.getSerialNumber().toString();
                if (sid != null) {
                    // we need this to be more unique than just DN - for example when certificate is regenerated
                    // in K8S/OpenShift environment
                    alias += "|" + sid;
                }
                pTrustStore.setCertificateEntry(alias, cert);
            }
        }
    }

    /**
     * Update a keystore with a Server certificate and key (stored as <em>(trusted) key entry</em>)
     *
     * @param pKeyStore   keystore to update
     * @param pServerCert server certificate in PEM or DER format - should contain one certificate
     * @param pServerKey  server key in DER/PEM format encoded using PKCS#1 or PKCS#8 matching the {@code pServerCert}
     * @param pKeyAlgo    algorithm used in the keystore (e.g. "RSA")
     * @param pPassword   password to use for the key file. must not be null, use <code>new char[0]</code>
     *                    for an empty password.
     */
    public static void updateWithServerCertificate(KeyStore pKeyStore, File pServerCert, File pServerKey, String pKeyAlgo, char[] pPassword)
            throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException {
        try (InputStream is = new FileInputStream(pServerCert)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            Certificate[] certificates = certFactory.generateCertificates(is).toArray(new Certificate[1]);

            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(pServerKey);
            byte[] keyBytes = cryptoData.derData();

            KeySpec keySpec = CryptoUtil.decodePrivateKey(cryptoData, pPassword);

            PrivateKey privateKey = CryptoUtil.generatePrivateKey(keySpec, pKeyAlgo);

            // check if these match
            X509Certificate x509Certificate = (X509Certificate) certificates[0];

            if (certificates.length == 1 && !CryptoUtil.keysMatch(privateKey, x509Certificate.getPublicKey())) {
                throw new IllegalArgumentException("Private key from " + pServerKey + " and public key from " + pServerCert + " do not match");
            }

            String alias = x509Certificate.getSubjectX500Principal().getName();
            pKeyStore.setKeyEntry(alias, privateKey, pPassword, certificates);
        }
    }

    /**
     * Update the given keystore with a self-signed server certificate. This can be used if no
     * server certificate is provided from the outside and no SSL verification is used by the client.
     *
     * @param pKeyStore keystore to update
     * @param pConfig
     */
    public static void updateWithSelfSignedServerCertificate(KeyStore pKeyStore, JolokiaServerConfig pConfig)
            throws NoSuchAlgorithmException, KeyStoreException {

        final String[] certAttributes = { "Jolokia Agent " + Version.getAgentVersion(), // CN
                                          "JVM",                                        // OU
                                          "jolokia.org",                                // O
                                          "Pegnitz",                                    // L
                                          "Franconia",                                  // ST
                                          "DE" };                                       // C

        // Need to do it via reflection because Java8 moved class to a different package
        KeyPair keypair = createRSAKeyPair();
        PrivateKey privKey = keypair.getPrivate();

        X509Certificate[] chain = new X509Certificate[1];
        Date from = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        cal.add(Calendar.YEAR, 1);
        Date to = cal.getTime();
        chain[0] = generateSelfCertificate(keypair, certAttributes, from, to.getTime() - from.getTime(), pConfig);
        pKeyStore.setKeyEntry("jolokia-agent", privKey, new char[0], chain);
    }

    private static KeyPair createRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096);
        return kpg.generateKeyPair();
    }

    /**
     * Generate self-signed X.509 certificate used as a server certificate.
     *
     * @param keypair
     * @param attributes
     * @param fromDate
     * @param valid
     * @param pConfig
     * @return
     */
    private static X509Certificate generateSelfCertificate(KeyPair keypair, String[] attributes, Date fromDate, long valid, JolokiaServerConfig pConfig) throws NoSuchAlgorithmException {
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
        DERInteger serialNumber = new DERInteger(BigInteger.valueOf(new Date().getTime()));
        DERSequence signature = new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_sha512WithRSAEncryption),
                new DERNull()
        });
        DERSequence issuerAndSubject = new DERSequence(new DERObject[] {
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_countryName),
                        new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, attributes[5])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_stateOrProvinceName),
                        new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, attributes[4])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_localityName),
                        new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, attributes[3])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_organizationName),
                        new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, attributes[2])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_organizationalUnitName),
                        new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, attributes[1])
                }) }),
                new DERSet(new DERObject[] { new DERSequence(new DERObject[] {
                        new DERObjectIdentifier(DERObjectIdentifier.OID_commonName),
                        new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, attributes[0])
                }) })
        });
        DERSequence validity = new DERSequence(new DERObject[] {
                new DERUtcTime(fromDate),
                new DERUtcTime(new Date(fromDate.getTime() + valid))
        });

        DERDirect subjectPublicKeyInfo = new DERDirect(keypair.getPublic().getEncoded());

        // see: https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.2
        // For CA certificates, subject key identifiers SHOULD be derived from
        //   the public key or a method that generates unique values.  Two common
        //   methods for generating key identifiers from the public key are:
        //      (1) The keyIdentifier is composed of the 160-bit SHA-1 hash of the
        //           value of the BIT STRING subjectPublicKey (excluding the tag,
        //           length, and number of unused bits).
        //      (2) The keyIdentifier is composed of a four-bit type field with
        //           the value 0100 followed by the least significant 60 bits of
        //           the SHA-1 hash of the value of the BIT STRING
        //           subjectPublicKey (excluding the tag, length, and number of
        //           unused bits).
        MessageDigest publicKeyDigest = MessageDigest.getInstance("SHA1");
        byte[] caPublicKeySHA1 = publicKeyDigest.digest(keypair.getPublic().getEncoded());

        // for subjectAltName X.509 extension
        final List<DERObject> altNames = new ArrayList<>();
        // IP:127.0.0.1
        altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 7, new DERDirect(new byte[] { 127, 0, 0, 1 })));
        // IP:0:0:0:0:0:0:0:1
        altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 7, new DERDirect(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 })));
        // DNS:localhost
        altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 2, new DERDirect("localhost".getBytes(StandardCharsets.UTF_8))));
        final Map<String, String> networkConfig = pConfig == null ? Collections.emptyMap() : pConfig.getJolokiaConfig().getNetworkConfig();
        NetworkUtil.getBestMatchAddresses().forEach((name, addresses) -> {
            addresses.getIa4().ifPresent(ip4 -> {
                if (!ip4.isLoopbackAddress()) {
                    altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 7, new DERDirect(ip4.getAddress())));
                    if (networkConfig.containsKey("host:" + name)) {
                        String host4 = networkConfig.get("host:" + name);
                        String ip4address = networkConfig.get("ip:" + name);
                        if (ip4address != null && !host4.isEmpty() && !host4.startsWith(ip4address)) {
                            altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 2, new DERDirect(host4.getBytes(StandardCharsets.UTF_8))));
                        }
                    }
                }
            });
            addresses.getIa6().ifPresent(ip6 -> {
                if (!ip6.isLoopbackAddress()) {
                    altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 7, new DERDirect(ip6.getAddress())));
                    if (networkConfig.containsKey("host6:" + name)) {
                        String host6 = networkConfig.get("host6:" + name);
                        String ip6address = networkConfig.get("ip6:" + name);
                        // check if hostname is <IP6>%eth0 for example
                        if (ip6address != null && !host6.isEmpty() && !host6.startsWith(ip6address)) {
                            altNames.add(new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 2, new DERDirect(host6.getBytes(StandardCharsets.UTF_8))));
                        }
                    }
                }
            });
        });

        DERSequence extensionsSeq = new DERSequence(new DERObject[] {
            // BasicConstraints ::= SEQUENCE {
            //        cA                      BOOLEAN DEFAULT FALSE,
            //        pathLenConstraint       INTEGER (0..MAX) OPTIONAL }
            new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_basicConstraints),
                new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, new DERSequence(new DERObject[] {
                    new DERBoolean(false)
                }).getEncoded())
            }),
            // KeyUsage ::= BIT STRING {
            //           digitalSignature        (0),
            //           nonRepudiation          (1), -- recent editions of X.509 have
            //                                -- renamed this bit to contentCommitment
            //           keyEncipherment         (2),
            //           dataEncipherment        (3),
            //           keyAgreement            (4),
            //           keyCertSign             (5),
            //           cRLSign                 (6),
            //           encipherOnly            (7),
            //           decipherOnly            (8) }
            new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_keyUsage),
                new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, new DERBitString(new byte[] {
                    (byte) 0b11111110
                }).getEncoded())
            }),
            // AuthorityKeyIdentifier ::= SEQUENCE {
            //      keyIdentifier             [0] KeyIdentifier           OPTIONAL,
            //      authorityCertIssuer       [1] GeneralNames            OPTIONAL,
            //      authorityCertSerialNumber [2] CertificateSerialNumber OPTIONAL  }
            //
            // KeyIdentifier ::= OCTET STRING
            //
            // authorityKeyIdentifier extension is a sequence with tagged objects
            new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_authorityKeyIdentifier),
                new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, new DERSequence(new DERObject[] {
                    new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, true, 0,
                        new DERDirect(caPublicKeySHA1)
                    )
                }).getEncoded())
            }),
            // SubjectKeyIdentifier ::= KeyIdentifier
            //
            // KeyIdentifier ::= OCTET STRING
            //
            // subjectKeyIdentifier extension is just the digest
            new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_subjectKeyIdentifier),
                new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, new DEROctetString(
                    DEROctetString.DER_OCTETSTRING_TAG, caPublicKeySHA1
                ).getEncoded())
            }),
            // ExtKeyUsageSyntax ::= SEQUENCE SIZE (1..MAX) OF KeyPurposeId
            //
            // KeyPurposeId ::= OBJECT IDENTIFIER
            new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_extKeyUsage),
                new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, new DERSequence(new DERObject[] {
                    new DERObjectIdentifier(DERObjectIdentifier.OID_id_kp_serverAuth)
                }).getEncoded())
            }),
            // SubjectAltName ::= GeneralNames
            //
            // GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
            //
            // GeneralName ::= CHOICE {
            //      otherName                       [0]     OtherName,
            //      rfc822Name                      [1]     IA5String,
            //      dNSName                         [2]     IA5String,
            //      x400Address                     [3]     ORAddress,
            //      directoryName                   [4]     Name,
            //      ediPartyName                    [5]     EDIPartyName,
            //      uniformResourceIdentifier       [6]     IA5String,
            //      iPAddress                       [7]     OCTET STRING,
            //      registeredID                    [8]     OBJECT IDENTIFIER }
            //
            // OtherName ::= SEQUENCE {
            //      type-id    OBJECT IDENTIFIER,
            //      value      [0] EXPLICIT ANY DEFINED BY type-id }
            //
            // EDIPartyName ::= SEQUENCE {
            //      nameAssigner            [0]     DirectoryString OPTIONAL,
            //      partyName               [1]     DirectoryString }
            new DERSequence(new DERObject[] {
                new DERObjectIdentifier(DERObjectIdentifier.OID_subjectAltName),
                new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, new DERSequence(altNames.toArray(new DERObject[0])).getEncoded())
            })
        });
        DERTaggedObject extensions = new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, false, 3, extensionsSeq);

        DERSequence tbsCertificate = new DERSequence(new DERObject[] {
                version,
                serialNumber,
                signature,
                issuerAndSubject,
                validity,
                issuerAndSubject,
                subjectPublicKeyInfo,
                extensions
        });

        try {
            Signature sig = Signature.getInstance("SHA512withRSA");
            sig.initSign(keypair.getPrivate(), SecureRandom.getInstance("SHA1PRNG"));
            sig.update(tbsCertificate.getEncoded());
            byte[] signatureBytes = sig.sign();

            DERSequence certificate = new DERSequence(new DERObject[] {
                    tbsCertificate,
                    new DERSequence(new DERObject[] {
                            new DERObjectIdentifier(DERObjectIdentifier.OID_sha512WithRSAEncryption),
                            new DERNull()
                    }),
                    new DERBitString(signatureBytes)
            });

            CertificateFactory cf = CertificateFactory.getInstance("X509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
        } catch (final InvalidKeyException | NoSuchAlgorithmException | SignatureException | CertificateException e) {
            throw new IllegalStateException("The getSelfCertificate-method threw an error.", e);
        }
    }

}
