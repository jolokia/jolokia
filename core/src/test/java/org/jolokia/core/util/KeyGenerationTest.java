/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.core.util;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.DSAParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.List;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.jolokia.asn1.DERObject;
import org.jolokia.asn1.DERObjectIdentifier;
import org.jolokia.asn1.DERSequence;
import org.jolokia.asn1.DERSet;
import org.jolokia.asn1.DERUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

//@Ignore("Run manually, rather an API showcase than a real test")
public class KeyGenerationTest {

    public static final Logger LOG = LoggerFactory.getLogger(KeyGenerationTest.class);

    @Test
    public void generatingAndEncodingKeys() throws Exception {
        // Temurin-17.0.17+10 - KeyPairGenerator algorithms
        List<String> algorithms = List.of("DSA", "RSA", "RSASSA-PSS", "EC", "Ed25519", "Ed448", "EdDSA", "X25519", "X448", "XDH", "DiffieHellman");
        File dir = new File("target/generated-keys");
        dir.mkdirs();
        Random rnd = SecureRandom.getInstance("SHA1PRNG");

        // here are the _new_ openssl equivalents (`openssl genrsa`, `openssl gendsa`, ..., `openssl rsa`, ...)
        // should be considered legacy:
        //  - "DSA"           - openssl genpkey -algorithm DSA
        //  - "RSA"           - openssl genpkey -algorithm RSA
        //  - "RSASSA-PSS"    - openssl genpkey -algorithm RSA-PSS
        //  - "EC"            - openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1
        //  - "Ed25519"       - openssl genpkey -algorithm ED25519
        //  - "Ed448"         - openssl genpkey -algorithm ED448
        //  - "EdDSA"         - _abstract umbrella_ in Java
        //  - "X25519"        - openssl genpkey -algorithm X25519
        //  - "X448"          - openssl genpkey -algorithm X448
        //  - "XDH"           - _abstract umbrella_ in Java
        //  - "DiffieHellman" - openssl genpkey -algorithm DH
        //
        // there are two key-related openssl list commands:
        //  - openssl list -kem-algorithms
        //  - openssl list -key-managers
        //
        // Key Managers in openssl knows how to:
        //  - Generate keys
        //  - Import/export ASN.1
        //  - Encode PKCS#8
        //  - Perform operations (sign, derive, decrypt)
        // and is kind of an equivalent to Java's JCA/JCE Provider + KeyFactory + KeyPairGenerator
        //
        // KEM = Key Encapsulation Mechanism - These are key-agreement / key-exchange primitives, not signatures
        //
        // EVP_PKEY:
        // +-- key manager (how to handle keys)
        // +-- KEM (how to derive secrets)
        // +-- signature (how to sign)
        // +-- encoding (PKCS#8, DER, PEM)

        // I could read _all_ the written keys (PKCS8, PKCS8 + PBES1, PKCS8 + PBES2) using `openssl pkey -inform der -in`
        // for `openssl pkcs8` I need `-nocrypt` for non-encrypted keys
        // `openssl pkey -traditional` allows output of the private key in "old" format (e.g., PKCS1 for RSA)
        // `openssl pkey -traditional -<enc>` allows output in PKCS1 format + DEK Info
        // only 3 algorithms support "traditional" output:
        //  - RSA - PKCS1
        //  - DSA - https://www.rfc-editor.org/rfc/rfc5480.html#appendix-A
        //  - EC - RFC 5915
        // to output public key in "traditional" form we have to use:
        //  - RSA - `openssl rsa -RSAPublicKey_out`
        //  - DSA - `openssl dsa -pubout | openssl asn1parse -strparse` - but this is just INTEGER
        //  - EC - not even `-strparse` works

        // PKCS#1: https://oid-base.com/get/1.2.840.113549.1.1 - RSA
        // PKCS#5: https://oid-base.com/get/1.2.840.113549.1.5 - Password-Based Cryptography - RFC 8018
        // PKCS#8: https://oid-base.com/get/1.2.840.113549.1.8 - Private-Key Information Syntax - RFC 5208
        //
        // Additionally `openssl pkcs8` uses "scrypt" - https://www.rfc-editor.org/rfc/rfc7914.html

        for (String algorithm : algorithms) {
            LOG.info("");
            LOG.info("=== Checking {}", algorithm);
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
            LOG.info("    KeyPairGenerator: {}", kpg.getClass().getName());
            KeyPair pair = kpg.generateKeyPair();
            assertNotNull(pair.getPrivate().getEncoded());

            Files.write(new File(dir, algorithm + "-pkcs8.der").toPath(), pair.getPrivate().getEncoded(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.write(new File(dir, algorithm + "-pkcs8.pem").toPath(), CryptoUtil.encodePem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            // should be parsable by Jolokia DER parser
            DERObject parsed = DERUtils.parse(pair.getPrivate().getEncoded());
            assertNotNull(parsed);

            // should be in PKCS8 format - PKCS8EncodedKeySpec is a simple record without much processing
            // non-encrypted PKCS8 private key is:
            //   PrivateKeyInfo ::= SEQUENCE {
            //     version                   Version,
            //     privateKeyAlgorithm       PrivateKeyAlgorithmIdentifier,
            //     privateKey                PrivateKey,
            //     attributes           [0]  IMPLICIT Attributes OPTIONAL }
            //
            //   Version ::= INTEGER
            //
            //   PrivateKeyAlgorithmIdentifier ::= AlgorithmIdentifier
            //
            //   PrivateKey ::= OCTET STRING
            //
            //   Attributes ::= SET OF Attribute

            DERObject[] algId = ((DERSequence) (((DERSequence) parsed).getValues()[1])).getValues();
            LOG.info("    Algorithm: {}", ((DERObjectIdentifier) algId[0]).asOid());
            LOG.info("    Parameters: {}", algId.length < 2 ? "<not present>" : algId[1].getTagAsString());
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pair.getPrivate().getEncoded(), algorithm);
            assertEquals(spec.getAlgorithm(), algorithm);

            // should be possible to recreate the key
            PrivateKey pkey = KeyFactory.getInstance(algorithm).generatePrivate(spec);
            assertEquals(pkey, pair.getPrivate());

            // the PrivateKeyAlgorithmIdentifier is AlgorithmIdentifier defined in X.509 6.2.2
            // AlgorithmIdentifier{ALGORITHM:SupportedAlgorithms} ::= SEQUENCE {
            //   algorithm  ALGORITHM.&id({SupportedAlgorithms}),
            //   parameters ALGORITHM.&Type({SupportedAlgorithms}{@algorithm}) OPTIONAL,
            // }
            // The algorithm component shall be an object identifier that uniquely identifies the cryptographic
            // algorithm being defined.
            // The parameters component, when present, shall specify the parameters associated with the algorithm.
            // Some, but not all algorithms require associated parameters.

            // In Java, the parameters are specified using AlgorithmParameters / AlgorithmParameterSpec
            // and each KeyPairGenerator algorithm, should be usable with AlgorithmParameters.getinstance()
            // (if there are any parameters of given key)
            AlgorithmParameters parameters;

            DERObject specEncoded = DERUtils.parse(spec.getEncoded());
            // we know it's PKCS#8, so 2nd element of 2nd sequence is the parameters

            try {
                parameters = AlgorithmParameters.getInstance(algorithm);
//                LOG.info("    AlgorithmParameters: {}", paramsSpi(parameters));
                if (algId.length > 1) {
                    parameters.init(algId[1].getEncoded());
                    printDer(DERUtils.parse(algId[1].getEncoded()), 4);
                    switch (algorithm) {
                        case "DSA": {
                            DSAParameterSpec dsaParameterSpec = parameters.getParameterSpec(DSAParameterSpec.class);
                            assertNotNull(dsaParameterSpec);
                            LOG.info("    AlgorithmParameterSpec: {}, (q = 0x{})", dsaParameterSpec, dsaParameterSpec.getQ().toString(16));
                            break;
                        }
                        case "RSASSA-PSS": {
                            PSSParameterSpec pssParameterSpec = parameters.getParameterSpec(PSSParameterSpec.class);
                            assertNotNull(pssParameterSpec);
                            LOG.info("    AlgorithmParameterSpec: {}", pssParameterSpec);
                            break;
                        }
                        case "EC": {
                            ECParameterSpec ecParameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
                            assertNotNull(ecParameterSpec);
                            LOG.info("    AlgorithmParameterSpec: {}, (curve = {})", ecParameterSpec, ecParameterSpec.getCurve());
                            break;
                        }
                        case "DiffieHellman": {
                            DHParameterSpec dhParameterSpec = parameters.getParameterSpec(DHParameterSpec.class);
                            assertNotNull(dhParameterSpec);
                            LOG.info("    AlgorithmParameterSpec: {} (g = {}, l = {})", dhParameterSpec, dhParameterSpec.getG(), dhParameterSpec.getL());
                            break;
                        }
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                LOG.info("    AlgorithmParameters: <not available>");
            }

            // should be able to encrypt the key using PKCS8 https://www.ietf.org/rfc/rfc5208.html#section-6
            // where the encryptionAlgorithm uses PKCS5 (PBE)
            // encrypted PKCS8 private key is:
            //   EncryptedPrivateKeyInfo ::= SEQUENCE {
            //     encryptionAlgorithm  EncryptionAlgorithmIdentifier,
            //     encryptedData        EncryptedData }
            //
            //   EncryptionAlgorithmIdentifier ::= AlgorithmIdentifier
            //
            //   EncryptedData ::= OCTET STRING

            // PKCS#5 is all about Password-Based encryption
            // https://oid-base.com/get/1.2.840.113549.1.5
            // see com.sun.crypto.provider.PBES2Parameters.engineGetEncoded()

            // RSA encrypted key (openssl genrsa -aes256 ...) has this PKCS#8 EncryptedPrivateKeyInfo structure
            // SEQUENCE
            //   SEQUENCE
            //   | OBJECT            :PBES2
            //   | SEQUENCE
            //   |   SEQUENCE
            //   |   | OBJECT            :PBKDF2
            //   |   | SEQUENCE
            //   |   |   OCTET STRING      [HEX DUMP]:15D0937D1C36A172D553A2C09A0D50B1
            //   |   |   INTEGER           :0800
            //   |   |   SEQUENCE
            //   |   |     OBJECT            :hmacWithSHA256
            //   |   |     NULL
            //   |   SEQUENCE
            //   |     OBJECT            :aes-256-cbc
            //   |     OCTET STRING      [HEX DUMP]:CED40B64AFC94EF389A4DDD826A05D2B
            //   OCTET STRING      [HEX DUMP]:AA808A4A534A6F8E74D8646C7939E5F8DF9C21A0A41504B6B3A8...
            //
            // Using own DER parser (algorithm names added manually):
            // SEQUENCE
            //   SEQUENCE
            //   | 1.2.840.113549.1.5.13 - PBES2
            //   | SEQUENCE
            //   |   SEQUENCE (1)
            //   |   | 1.2.840.113549.1.5.12 - pBKDF2
            //   |   | SEQUENCE
            //   |   |   OCTETSTRING: <non-UTF8 string> - salt (see <grgr-salt> below)
            //   |   |   INTEGER: 2048 - iteration count (<grgr-iteration-count>)
            //   |   |   SEQUENCE - pseudo-random function
            //   |   |     1.2.840.113549.2.9 - hmacWithSHA256
            //   |   |     NULL: null
            //   |   SEQUENCE (2)
            //   |     2.16.840.1.101.3.4.1.42 - aes256-CBC-PAD
            //   |     OCTETSTRING: <non-UTF8 string>
            //   OCTETSTRING: <non-UTF8 string>
            //
            // Analyzing PKCS#5:
            // PBES2 = 1.2.840.113549.1.5.13. Parameters is a SEQUENCE with two elements:
            //  (1) - keyDerivationFunc AlgorithmIdentifier {{PBES2-KDFs}} - one defined - PBKDF2
            //  (2) - encryptionScheme AlgorithmIdentifier {{PBES2-Encs}} - any possible
            //
            // PBKDF2 has these parameters:
            //   PBKDF2Algorithms ALGORITHM-IDENTIFIER ::= {
            //      {PBKDF2-params IDENTIFIED BY id-PBKDF2},
            //      ...
            //   }
            //
            //   id-PBKDF2 OBJECT IDENTIFIER ::= {pkcs-5 12}
            //
            //   algid-hmacWithSHA1 AlgorithmIdentifier {{PBKDF2-PRFs}} ::=
            //      {algorithm id-hmacWithSHA1, parameters NULL : NULL}
            //
            //   PBKDF2-params ::= SEQUENCE {
            //       salt CHOICE {
            //         specified OCTET STRING, <grgr-salt>
            //         otherSource AlgorithmIdentifier {{PBKDF2-SaltSources}}
            //       },
            //       iterationCount INTEGER (1..MAX), <grgr-iteration-count>
            //       keyLength INTEGER (1..MAX) OPTIONAL,
            //       prf AlgorithmIdentifier {{PBKDF2-PRFs}} DEFAULT
            //       algid-hmacWithSHA1
            //   }
            //
            //   PBKDF2-SaltSources ALGORITHM-IDENTIFIER ::= { ... }
            //
            //   PBKDF2-PRFs ALGORITHM-IDENTIFIER ::= { - pseudo-random functions
            //     {NULL IDENTIFIED BY id-hmacWithSHA1},
            //     {NULL IDENTIFIED BY id-hmacWithSHA224},
            //     {NULL IDENTIFIED BY id-hmacWithSHA256},
            //     {NULL IDENTIFIED BY id-hmacWithSHA384},
            //     {NULL IDENTIFIED BY id-hmacWithSHA512},
            //     {NULL IDENTIFIED BY id-hmacWithSHA512-224},
            //     {NULL IDENTIFIED BY id-hmacWithSHA512-256},
            //     ...
            //   }

            // actually all the keys generated by `openssl genpkey -algorithm xxx -aes256 ...` use these:
            //  - PBES       - PBES2          - 1.2.840.113549.1.5.13
            //  - KDF        - PBKDF2         - 1.2.840.113549.1.5.12
            //  - PRF        - hmacWithSHA256 - 1.2.840.113549.2.9
            //  - encryption - aes-256-cbc    - 2.16.840.1.101.3.4.1.42

            // let's create ENCRYPTED PRIVATE KEY in pure Java, so it's usable with openssl

            // sun.security.util.KnownOIDs for PKCS#5:
            // // PKCS5 1.2.840.113549.1.5.*
            //  PBEWithMD5AndDES("1.2.840.113549.1.5.3"),
            //  PBEWithMD5AndRC2("1.2.840.113549.1.5.6"),
            //  PBEWithSHA1AndDES("1.2.840.113549.1.5.10"),
            //  PBEWithSHA1AndRC2("1.2.840.113549.1.5.11"),
            //  PBKDF2WithHmacSHA1("1.2.840.113549.1.5.12"),
            //  PBES2("1.2.840.113549.1.5.13"),

            // however we have these SecretKeyFactories:
            //  - DES
            //  - DESede
            //  - PBEWithHmacSHA1AndAES_128
            //  - PBEWithHmacSHA1AndAES_256
            //  - PBEWithHmacSHA224AndAES_128
            //  - PBEWithHmacSHA224AndAES_256
            //  - PBEWithHmacSHA256AndAES_128
            //  - PBEWithHmacSHA256AndAES_256
            //  - PBEWithHmacSHA384AndAES_128
            //  - PBEWithHmacSHA384AndAES_256
            //  - PBEWithHmacSHA512AndAES_128
            //  - PBEWithHmacSHA512AndAES_256
            //  - PBEWithMD5AndDES
            //  - PBEWithMD5AndTripleDES
            //  - PBEWithSHA1AndDESede
            //  - PBEWithSHA1AndRC2_128
            //  - PBEWithSHA1AndRC2_40
            //  - PBEWithSHA1AndRC4_128
            //  - PBEWithSHA1AndRC4_40
            //  - PBKDF2WithHmacSHA1   - no matching Cipher
            //  - PBKDF2WithHmacSHA224 - no matching Cipher
            //  - PBKDF2WithHmacSHA256 - no matching Cipher
            //  - PBKDF2WithHmacSHA384 - no matching Cipher
            //  - PBKDF2WithHmacSHA512 - no matching Cipher

            // and these Ciphers (with "pbe" in their name):
            //  - PBEWithHmacSHA1AndAES_128
            //  - PBEWithHmacSHA1AndAES_256
            //  - PBEWithHmacSHA224AndAES_128
            //  - PBEWithHmacSHA224AndAES_256
            //  - PBEWithHmacSHA256AndAES_128
            //  - PBEWithHmacSHA256AndAES_256
            //  - PBEWithHmacSHA384AndAES_128
            //  - PBEWithHmacSHA384AndAES_256
            //  - PBEWithHmacSHA512AndAES_128
            //  - PBEWithHmacSHA512AndAES_256
            //  - PBEWithMD5AndDES
            //  - PBEWithMD5AndTripleDES
            //  - PBEWithSHA1AndDESede
            //  - PBEWithSHA1AndRC2_128
            //  - PBEWithSHA1AndRC2_40
            //  - PBEWithSHA1AndRC4_128
            //  - PBEWithSHA1AndRC4_40

            // PKCS#5 defines two schemes: PBES1 (legacy) and PBES2
            //  - password is used to derive a key
            //  - key is used to encrypt the value/data (or private key from PKCS#8)
            // https://www.ietf.org/rfc/rfc8018.html#section-5 - Key Derivation Functions
            // PBKDF1 is not recommended (based on MD2, MD5 or SHA1)
            // PBKDF2 - PBKDF2WithHmacSHA1, PBKDF2WithHmacSHA224, PBKDF2WithHmacSHA256, PBKDF2WithHmacSHA384, PBKDF2WithHmacSHA512
            // https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#secretkeyfactory-algorithms
            // PBE - Secret-key factory for use with PKCS #5. use digest, pseudo-random function and encryption
            // PBKDF2 - Password-based key-derivation algorithm defined in PKCS #5.

            // In Java, PBKDF(2) is only a key derivation function, while PBE is full _scheme_ that includes
            // key derivation (using PBKDF), encrypting, hashing, ...
            // depending on what algorithms are used for "With" and "And", PBES1 or PBES2 will be used
            // https://www.ietf.org/rfc/rfc8018.html#section-6 - Encryption Schemes

            char[] password = "jolokia".toCharArray();

            // 1. easy way with PBES2

            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBEWithHmacSHA512AndAES_256");
            SecretKey sk1 = skf.generateSecret(new PBEKeySpec(password));
            // this key is still not _derived_:
            // sk = {com.sun.crypto.provider.PBEKey@3475}
            //   key: byte[]  = {byte[7]@3965} [106, 111, 108, 111, 107, 105, 97]
            //   type: java.lang.String  = {@3966} "PBEWithHmacSHA512AndAES_256"

            // PBEWithHmac* cipher uses an SPI that selects com.sun.crypto.provider.PBKDF2Core implementation
            // to derive a key from the initial PBEKeySpec available through SecretKey
            // defaults:
            //  - salt: com.sun.crypto.provider.PBES2Core.DEFAULT_SALT_LENGTH = 20
            //  - ic:   com.sun.crypto.provider.PBES2Core.DEFAULT_COUNT = 4096
            //  - IV:   com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE = 16
            Cipher pbeCipher = Cipher.getInstance(skf.getAlgorithm());
            byte[] salt = new byte[42];
            rnd.nextBytes(salt);
            byte[] iv = new byte[16];
            rnd.nextBytes(iv);
            PBEParameterSpec pbeParamsSpec = new PBEParameterSpec(salt, 13, new IvParameterSpec(iv));
            pbeCipher.init(Cipher.ENCRYPT_MODE, sk1, pbeParamsSpec);
            byte[] encKey = pbeCipher.doFinal(pair.getPrivate().getEncoded());

            // now how to save it as EncryptedPrivateKeyInfo?
            AlgorithmParameters pbes2params = AlgorithmParameters.getInstance(skf.getAlgorithm());
            pbes2params.init(pbeParamsSpec);

            // so far, so good. for DSA, DERUtils.parse(pbes2params.getEncoded()):
            // result = {grgr.javase.asn1.DERSequence@4062}
            //  values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4063}
            //   0 = {grgr.javase.asn1.DERSequence@4066}
            //    values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4068}
            //     0 = {grgr.javase.asn1.DERObjectIdentifier@4070} "1.2.840.113549.1.5.12"
            //     1 = {grgr.javase.asn1.DERSequence@4071}
            //      values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[4]@4076}
            //       0 = {grgr.javase.asn1.DEROctetString@4077} "<non-UTF8 string>" - salt
            //       1 = {grgr.javase.asn1.DERInteger@4078} "0xd" - iteration count
            //       2 = {grgr.javase.asn1.DERInteger@4079} "0x20" - key size (256 / 8 = 32 = 0x20)
            //       3 = {grgr.javase.asn1.DERSequence@4080}
            //        values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4082}
            //         0 = {grgr.javase.asn1.DERObjectIdentifier@4083} "1.2.840.113549.2.11"
            //         1 = {grgr.javase.asn1.DERNull@4084} "null" - null parameters of KDF
            //   1 = {grgr.javase.asn1.DERSequence@4067}
            //    values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4069}
            //     0 = {grgr.javase.asn1.DERObjectIdentifier@4073} "2.16.840.1.101.3.4.1.42"
            //     1 = {grgr.javase.asn1.DEROctetString@4074} "<non-UTF8 string>"

            // but this can't be passed to EncryptedPrivateKeyInfo - we need a wrapping algorithm
            AlgorithmParameters pbes2 = AlgorithmParameters.getInstance("PBES2");
            // java.security.AlgorithmParameters.getParameterSpec() takes only salt, ic and iv, but looses
            // the algorithms used
//            pbes2.init(pbes2params.getParameterSpec(PBEParameterSpec.class));
            // this is full encoded parameters that can be "attached" as PBES2 params
            pbes2.init(pbes2params.getEncoded());

            EncryptedPrivateKeyInfo encInfo = new EncryptedPrivateKeyInfo(pbes2, encKey);
            byte[] encryptedPkcs8 = encInfo.getEncoded();
            Files.write(new File(dir, algorithm + "-" + skf.getAlgorithm() + "-pkcs8-pbes2.der").toPath(), encryptedPkcs8);
            Files.write(new File(dir, algorithm + "-" + skf.getAlgorithm() + "-pkcs8-pbes2.pem").toPath(), CryptoUtil.encodePem("ENCRYPTED PRIVATE KEY", encryptedPkcs8));

            // 2. harder - we could do PBES2 manually by deriving the key ourselves...

            // 3. PBES1
            //  - https://www.rfc-editor.org/rfc/rfc8018.html#appendix-A.1: PBKDF1 - no OID for PBKDF1
            //  - https://www.rfc-editor.org/rfc/rfc8018.html#appendix-A.3: PBES1 - there seem to be no wrapper like PBES2

            skf = SecretKeyFactory.getInstance("PBEWithSHA1AndDESede");
            SecretKey sk2 = skf.generateSecret(new PBEKeySpec(password));

            // PBEWith<non-Hmac>* cipher uses an SPI that derives the key manually with:
            // 1) PBEWithMD5*
            //     - default ic = 10
            //     - default salt = 8 bytes (this is minimum)
            // 2) PBEWithSHA*
            //     - default ic = 1024
            //     - default salt = 20 bytes
            //     - iv size 8
            pbeCipher = Cipher.getInstance(skf.getAlgorithm());
            salt = new byte[42];
            rnd.nextBytes(salt);
            iv = new byte[16];
            rnd.nextBytes(iv);
            pbeParamsSpec = new PBEParameterSpec(salt, 13, new IvParameterSpec(iv));
            pbeCipher.init(Cipher.ENCRYPT_MODE, sk2, pbeParamsSpec);
            encKey = pbeCipher.doFinal(pair.getPrivate().getEncoded());

            // now how to save it as EncryptedPrivateKeyInfo?
            AlgorithmParameters pbes1params = AlgorithmParameters.getInstance(skf.getAlgorithm());
            pbes1params.init(pbeParamsSpec);

            // unlike as with PBES2, we can pass pbes1params directly
            // and openssl will read it! (where's IV stored?)
            //
            // DERUtils.parse(pbes1params.getEncoded()) is just:
            // result = {grgr.javase.asn1.DERSequence@4040}
            //  values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4041}
            //   0 = {grgr.javase.asn1.DEROctetString@4044} "<non-UTF8 string>"
            //   1 = {grgr.javase.asn1.DERInteger@4045} "0xd"

            // the funny thing is that even if in PBES1 there's IV, it is NOT stored in the DER structure, because
            // it is derived from the salt and ic and password as well! just with different key size (8)

            encInfo = new EncryptedPrivateKeyInfo(pbes1params, encKey);
            encryptedPkcs8 = encInfo.getEncoded();
            // entire info:
            // result = {grgr.javase.asn1.DERSequence@4074}
            //  values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4075}
            //   0 = {grgr.javase.asn1.DERSequence@4078}
            //    values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4080}
            //     0 = {grgr.javase.asn1.DERObjectIdentifier@4081} "1.2.840.113549.1.12.1.3"
            //     1 = {grgr.javase.asn1.DERSequence@4082}
            //      values: grgr.javase.asn1.DERObject[]  = {grgr.javase.asn1.DERObject[2]@4084}
            //       0 = {grgr.javase.asn1.DEROctetString@4085} "<non-UTF8 string>"
            //       1 = {grgr.javase.asn1.DERInteger@4086} "0xd"
            //   1 = {grgr.javase.asn1.DEROctetString@4079} "<non-UTF8 string>"
            Files.write(new File(dir, algorithm + "-" + skf.getAlgorithm() + "-pkcs8-pbes1.der").toPath(), encryptedPkcs8);
            Files.write(new File(dir, algorithm + "-" + skf.getAlgorithm() + "-pkcs8-pbes1.pem").toPath(), CryptoUtil.encodePem("ENCRYPTED PRIVATE KEY", encryptedPkcs8));
        }
    }

    @Test
    public void rsaPbeEncryption() throws Exception {
        File dir = new File("target/generated-rsa-keys");
        dir.mkdirs();
        Random rnd = SecureRandom.getInstance("SHA1PRNG");

        String algorithm = "RSA";
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
        KeyPair pair = kpg.generateKeyPair();
        assertNotNull(pair.getPrivate().getEncoded());

        Files.write(new File(dir, algorithm + "-pkcs8.pem").toPath(), CryptoUtil.encodePem("PRIVATE KEY", pair.getPrivate().getEncoded()),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        //  - com.sun.crypto.provider.PBEWithMD5AndDESCipher                    - uses com.sun.crypto.provider.PBES1Core("DES")
        //  - com.sun.crypto.provider.PBEWithMD5AndTripleDESCipher              - uses com.sun.crypto.provider.PBES1Core("DESede")
        //  - com.sun.crypto.provider.PKCS12PBECipherCore.PBEWithSHA1AndDESede  - uses com.sun.crypto.provider.PKCS12PBECipherCore("DESede", 24)
        //  - com.sun.crypto.provider.PKCS12PBECipherCore.PBEWithSHA1AndRC2_128 - uses com.sun.crypto.provider.PKCS12PBECipherCore("RC2", 16)
        //  - com.sun.crypto.provider.PKCS12PBECipherCore.PBEWithSHA1AndRC2_40  - uses com.sun.crypto.provider.PKCS12PBECipherCore("RC2", 5)
        //  - com.sun.crypto.provider.PKCS12PBECipherCore.PBEWithSHA1AndRC4_128 - uses com.sun.crypto.provider.PKCS12PBECipherCore("RC4", 16)
        //  - com.sun.crypto.provider.PKCS12PBECipherCore.PBEWithSHA1AndRC4_40  - uses com.sun.crypto.provider.PKCS12PBECipherCore("RC4", 5)
        List<String> pbes1Algorithms = List.of(
            "PBEWithMD5AndDES",            // OpenSSL: pbeWithMD5AndDES-CBC
//            "PBEWithMD5AndTripleDES",      // Java: unrecognized algorithm name: PBEWithMD5AndTripleDES in sun.security.x509.AlgorithmId.get()
            "PBEWithSHA1AndDESede",        // OpenSSL: pbeWithSHA1And3-KeyTripleDES-CBC (yes...) - only this one is
                                           //          interoperable between Java and OpenSSL (openssl pkcs8 -topk8 -v1 PBE-SHA1-3DES)
            "PBEWithSHA1AndRC2_128",       // OpenSSL: pbeWithSHA1And128BitRC2-CBC
            "PBEWithSHA1AndRC2_40",        // OpenSSL: pbeWithSHA1And40BitRC2-CBC
            "PBEWithSHA1AndRC4_128",       // OpenSSL: pbeWithSHA1And128BitRC4
            "PBEWithSHA1AndRC4_40"         // OpenSSL: pbeWithSHA1And40BitRC4
        );

        //                                                           |  kdf       | cipher | ksize    |
        // -----------------------------------------------------------------------+--------+----------+
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA1AndAES_128   | HmacSHA1   | AES    | 16*8=128 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA1AndAES_256   | HmacSHA1   | AES    | 32*8=256 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA224AndAES_128 | HmacSHA224 | AES    | 16*8=128 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA224AndAES_256 | HmacSHA224 | AES    | 32*8=256 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA256AndAES_128 | HmacSHA256 | AES    | 16*8=128 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA256AndAES_256 | HmacSHA256 | AES    | 32*8=256 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA384AndAES_128 | HmacSHA384 | AES    | 16*8=128 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA384AndAES_256 | HmacSHA384 | AES    | 32*8=256 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA512AndAES_128 | HmacSHA512 | AES    | 16*8=128 |
        //  - com.sun.crypto.provider.PBES2Core.HmacSHA512AndAES_256 | HmacSHA512 | AES    | 32*8=256 |
        List<String> pbes2Algorithms = List.of(
            "PBEWithHmacSHA1AndAES_128",   // OpenSSL: hmacWithSHA1 + aes-128-cbc
            "PBEWithHmacSHA1AndAES_256",   // OpenSSL: hmacWithSHA1 + aes-256-cbc
            "PBEWithHmacSHA224AndAES_128", // OpenSSL: hmacWithSHA224 + aes-128-cbc
            "PBEWithHmacSHA224AndAES_256", // OpenSSL: hmacWithSHA224 + aes-256-cbc
            "PBEWithHmacSHA256AndAES_128", // OpenSSL: hmacWithSHA256 + aes-128-cbc
            "PBEWithHmacSHA256AndAES_256", // OpenSSL: hmacWithSHA256 + aes-256-cbc
            "PBEWithHmacSHA384AndAES_128", // OpenSSL: hmacWithSHA384 + aes-128-cbc
            "PBEWithHmacSHA384AndAES_256", // OpenSSL: hmacWithSHA384 + aes-256-cbc
            "PBEWithHmacSHA512AndAES_128", // OpenSSL: hmacWithSHA512 + aes-128-cbc
            "PBEWithHmacSHA512AndAES_256"  // OpenSSL: hmacWithSHA512 + aes-256-cbc
        );

        // sun.security.util.KnownOIDs for PKCS#5:
        // // PKCS5 1.2.840.113549.1.5.*
        //  PBEWithMD5AndDES("1.2.840.113549.1.5.3"),
        //  PBEWithMD5AndRC2("1.2.840.113549.1.5.6"),
        //  PBEWithSHA1AndDES("1.2.840.113549.1.5.10"),
        //  PBEWithSHA1AndRC2("1.2.840.113549.1.5.11"),
        //  PBKDF2WithHmacSHA1("1.2.840.113549.1.5.12"),
        //  PBES2("1.2.840.113549.1.5.13"),

        char[] password = "jolokia".toCharArray();

        for (String pbe : pbes1Algorithms) {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(pbe);
            SecretKey sk = skf.generateSecret(new PBEKeySpec(password));

            Cipher pbeCipher = Cipher.getInstance(skf.getAlgorithm());
            byte[] salt = pbe.contains("WithMD5") ? new byte[8] : new byte[42];
            rnd.nextBytes(salt);
            byte[] iv = new byte[16];
            rnd.nextBytes(iv);
            PBEParameterSpec pbeParamsSpec = new PBEParameterSpec(salt, 13, new IvParameterSpec(iv));
            pbeCipher.init(Cipher.ENCRYPT_MODE, sk, pbeParamsSpec);
            byte[] encKey = pbeCipher.doFinal(pair.getPrivate().getEncoded());

            AlgorithmParameters pbes1params = AlgorithmParameters.getInstance(skf.getAlgorithm());
            pbes1params.init(pbeParamsSpec);

            EncryptedPrivateKeyInfo encInfo = new EncryptedPrivateKeyInfo(pbeCipher.getParameters(), encKey);
            // or:
//            EncryptedPrivateKeyInfo encInfo = new EncryptedPrivateKeyInfo(pbes1params, encKey);
            byte[] encryptedPkcs8 = encInfo.getEncoded();

            Files.write(new File(dir, algorithm + "-" + skf.getAlgorithm() + "-pkcs8-pbes1.pem").toPath(), CryptoUtil.encodePem("ENCRYPTED PRIVATE KEY", encryptedPkcs8));
        }

        for (String pbe : pbes2Algorithms) {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(pbe);
            SecretKey sk = skf.generateSecret(new PBEKeySpec(password));

            Cipher pbeCipher = Cipher.getInstance(skf.getAlgorithm());
            byte[] salt = new byte[42];
            rnd.nextBytes(salt);
            byte[] iv = new byte[16];
            rnd.nextBytes(iv);
            PBEParameterSpec pbeParamsSpec = new PBEParameterSpec(salt, 13, new IvParameterSpec(iv));
            pbeCipher.init(Cipher.ENCRYPT_MODE, sk, pbeParamsSpec);
            byte[] encKey = pbeCipher.doFinal(pair.getPrivate().getEncoded());

            AlgorithmParameters pbes2params = AlgorithmParameters.getInstance(skf.getAlgorithm());
            pbes2params.init(pbeParamsSpec);

            AlgorithmParameters pbes2 = AlgorithmParameters.getInstance("PBES2");
            pbes2.init(pbes2params.getEncoded());

            EncryptedPrivateKeyInfo encInfo = new EncryptedPrivateKeyInfo(pbes2, encKey);
            byte[] encryptedPkcs8 = encInfo.getEncoded();
            Files.write(new File(dir, algorithm + "-" + skf.getAlgorithm() + "-pkcs8-pbes2.pem").toPath(), CryptoUtil.encodePem("ENCRYPTED PRIVATE KEY", encryptedPkcs8));
        }
    }

    private Class<?> paramsSpi(AlgorithmParameters parameters) throws Exception {
        Field f = parameters.getClass().getDeclaredField("paramSpi");
        f.setAccessible(true);
        return f.get(parameters).getClass();
    }

    private String skfSpi(SecretKeyFactory skf) throws Exception {
        Field f = skf.getClass().getDeclaredField("spi");
        f.setAccessible(true);
        return f.get(skf).getClass().getName();
    }

    public static void printDer(DERObject der, int indent) {
        for (int i = 0; i < indent; i++) {
            System.out.print("  ");
        }
        if (der instanceof DERObjectIdentifier oid) {
            System.out.println("OID: " + oid.asOid());
        } else if (der instanceof DERSequence sequence) {
            System.out.println(sequence.getTagAsString());
            for (DERObject el : sequence.getValues()) {
                printDer(el, indent + 1);
            }
        } else if (der instanceof DERSet set) {
            System.out.println(set.getTagAsString());
            for (DERObject el : set.getValues()) {
                printDer(el, indent + 1);
            }
        } else {
            System.out.println(der.getTagAsString() + ": " + der.toString());
        }
    }

}
