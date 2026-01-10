/*
 * Copyright 2015-2026
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.jolokia.asn1.DERBitString;
import org.jolokia.asn1.DERInteger;
import org.jolokia.asn1.DERObject;
import org.jolokia.asn1.DERObjectIdentifier;
import org.jolokia.asn1.DEROctetString;
import org.jolokia.asn1.DERSequence;
import org.jolokia.asn1.DERUtils;

/**
 * <p>This code was originally inspired and taken over from net.auth.core:oauth
 * (albeit in a highly stripped variation): Source is from
 * http://oauth.googlecode.com/svn/code/java/ which is licensed under the APL
 * (http://oauth.googlecode.com/svn/code/java/LICENSE.txt).
 * All credits go to the original author (zhang)</p>
 *
 * <p>New version uses Jolokia own DER support and adds functionality for handling all private key PEMs
 * (encrypted PKCS8, non-encrypted PKCS8, PKCS1 (RSA)).</p>
 *
 * @author roland
 * @since 30/09/15
 */
public class CryptoUtil {

    private CryptoUtil() {
    }

    /**
     * <p>Read DER wrapped in {@link CryptoStructure} and return a supported {@link KeySpec}:<ul>
     *     <li>PKCS#1 RSAPublicKey from <a href="https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.1">RFC 8017</a></li>
     *     <li>SubjectPublicKeyInfo from <a href="https://datatracker.ietf.org/doc/html/rfc5280#section-4.1">RFC 5280</a></li>
     * </ul></p>
     *
     * <p>Most public keys are wrapped in X.509 {@code SubjectPublicKeyInfo} and have PEM marker {@code PUBLIC KEY}. RSA
     * is kind of special ({@code BEGIN RSA PUBLIC KEY}), while other algorithms usually use single value, so they have
     * no dedicated ASN.1 structure</p>
     *
     * @param cryptoStructure
     * @return
     */
    public static KeySpec decodePublicKey(CryptoStructure cryptoStructure) {
        return decodePublicKey(cryptoStructure, true);
    }

    private static KeySpec decodePublicKey(CryptoStructure cryptoStructure, boolean firstCheck) {
        DERObject object = DERUtils.parse(cryptoStructure.derData());
        if (!(object instanceof DERSequence sequence)) {
            throw new IllegalArgumentException("Expected a public key encoded as ASN.1 SEQUENCE");
        }
        DERObject[] seq = sequence.getValues();

        if (cryptoStructure.hint == StructureHint.DER) {
            if (!firstCheck) {
                throw new IllegalStateException("[internal, please report] Can't do another identification of a DER structure");
            }
            if (seq.length == 2 && (seq[0] instanceof DERSequence) && (seq[1] instanceof DERBitString)) {
                // try SubjectPublicKeyInfo
                return decodePublicKey(cryptoStructure.withNewHint(StructureHint.X509_SUBJECT_PUBLIC_KEY_INFO), false);
            }
            if (seq.length == 2 && (seq[0] instanceof DERInteger n) && (seq[1] instanceof DERInteger e)) {
                // try RSAPublicKey
                return decodePublicKey(cryptoStructure.withNewHint(StructureHint.RSA_PUBLIC_KEY), false);
            }
        } else if (cryptoStructure.hint == StructureHint.X509_SUBJECT_PUBLIC_KEY_INFO) {
            // https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.7
            // SubjectPublicKeyInfo  ::=  SEQUENCE  {
            //     algorithm            AlgorithmIdentifier,
            //     subjectPublicKey     BIT STRING
            // }
            // just check if we can create java.security.spec.X509EncodedKeySpec
            if (seq.length != 2 || !((seq[0] instanceof DERSequence alg) && seq[1] instanceof DERBitString)) {
                throw new IllegalArgumentException("SubjectPublicKeyInfo requires 2-element ASN.1 Sequence: algorithm:AlgorithmIdentifier and subjectPublicKey:BITSTRING");
            }

            if (alg.getValues().length > 0 && (alg.getValues()[0] instanceof DERObjectIdentifier oid)) {
                if (DERObjectIdentifier.SUPPORTED_X509_PUBLIC_KEYS.contains(oid.asOid())) {
                    return new X509EncodedKeySpec(cryptoStructure.derData());
                } else {
                    throw new IllegalArgumentException("Unsupported SubjectPublicKeyInfo.algorithm: " + oid.asOid());
                }
            }
            throw new IllegalArgumentException("SubjectPublicKeyInfo not found in the ASN.1 structure");
        } else if (cryptoStructure.hint == StructureHint.RSA_PUBLIC_KEY) {
            // https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.1
            // RSAPublicKey ::= SEQUENCE {
            //     modulus           INTEGER,  -- n
            //     publicExponent    INTEGER   -- e
            // }
            if (seq.length != 2 || !(seq[0] instanceof DERInteger && seq[1] instanceof DERInteger)) {
                throw new IllegalArgumentException("RSAPublicKey requires 2-element ASN.1 Sequence: modulus:INTEGER and publicExponent:INTEGER");
            }
            DERObject modulus = seq[0];
            DERObject publicExponent = seq[1];
            return new RSAPublicKeySpec(((DERInteger) modulus).asBigInteger(), ((DERInteger) publicExponent).asBigInteger());
        }

        if (cryptoStructure.hint == StructureHint.DER || !firstCheck) {
            // when not first check, it means the first check was for DER structure
            throw new IllegalArgumentException("Can't read public key from DER structure");
        } else {
            throw new IllegalArgumentException("Can't handle public keys in \"" + cryptoStructure.pemMarker + "\" format");
        }
    }

    /**
     * <p>Read DER wrapped in {@link CryptoStructure} and return a supported {@code KeySpec}:<ul>
     *     <li>PKCS#8 non-encrypted key ({@code PrivateKeyInfo}</li>
     *     <li>PKCS#8 encrypted key ({@code EncryptedPrivateKeyInfo}</li>
     *     <li>PKCS#1 RSAPrivateKey from <a href="https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.2">RFC 8017</a</li>
     *     <li>DSA Private Key (reverse-engineered from {@code openssl pkey -traditional})</li>
     *     <li>EC Private Key (reverse-engineered from {@code openssl pkey -traditional})</li>
     *     <li>Legacy encrypted keys using <a href="https://docs.openssl.org/1.1.1/man3/PEM_read_bio_PrivateKey/#pem-encryption-format">OpenSSL RSA/DSA/EC PEM encryption</a></li>
     * </ul></p>
     *
     * @param cryptoStructure
     * @param password - required for encrypted keys
     * @return
     */
    public static KeySpec decodePrivateKey(CryptoStructure cryptoStructure, char[] password) {
        return decodePrivateKey(cryptoStructure, password, true);
    }

    public static KeySpec decodePrivateKey(CryptoStructure cryptoStructure, char[] password, boolean firstCheck) {
        DERObject object = DERUtils.parse(cryptoStructure.derData());
        if (!(object instanceof DERSequence sequence)) {
            throw new IllegalArgumentException("Expected a private key encoded as ASN.1 SEQUENCE");
        }
        DERObject[] seq = sequence.getValues();

        if (cryptoStructure.hint == StructureHint.DER) {
            // we have to guess - there was no PEM marker
            if (!firstCheck) {
                throw new IllegalStateException("[internal, please report] Can't do another identification of a DER structure");
            }
            if (seq.length >= 3 && seq[0] instanceof DERInteger && seq[1] instanceof DERSequence && seq[2] instanceof DEROctetString) {
                // try PKCS#8 PrivateKeyInfo
                return decodePrivateKey(cryptoStructure.withNewHint(StructureHint.PKCS8_PRIVATE_KEY), password, false);
            }
            if (seq.length >= 9 && Arrays.stream(seq).limit(9).allMatch(el -> el instanceof DERInteger)) {
                // try RSAPrivateKey
                return decodePrivateKey(cryptoStructure.withNewHint(StructureHint.RSA_PRIVATE_KEY), password, false);
            }
            if (seq.length == 2 && seq[0] instanceof DERSequence && seq[1] instanceof DEROctetString) {
                // try EncryptedPrivateKeyInfo
                return decodePrivateKey(cryptoStructure.withNewHint(StructureHint.PKCS8_ENCRYPTED_PRIVATE_KEY), password, false);
            }
        } else if (cryptoStructure.hint == StructureHint.RSA_PRIVATE_KEY) {
            // https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.2
            // RSAPrivateKey ::= SEQUENCE {
            //     version           Version,
            //     modulus           INTEGER,  -- n
            //     publicExponent    INTEGER,  -- e
            //     privateExponent   INTEGER,  -- d
            //     prime1            INTEGER,  -- p
            //     prime2            INTEGER,  -- q
            //     exponent1         INTEGER,  -- d mod (p-1)
            //     exponent2         INTEGER,  -- d mod (q-1)
            //     coefficient       INTEGER,  -- (inverse of q) mod p
            //     otherPrimeInfos   OtherPrimeInfos OPTIONAL
            // }
            // Version ::= INTEGER { two-prime(0), multi(1) }

            if (seq.length < 9) {
                throw new IllegalArgumentException("ASN.1 SEQUENCE for PKCS#1 RSA private key should contain at least 9 ASN.1 Integers");
            }
            int intCount = 0;
            for (DERObject el : seq) {
                if (intCount >= 9) {
                    break;
                }
                if (!(el instanceof DERInteger)) {
                    throw new IllegalArgumentException("Unexpected ASN.1 element found. Expected ASN.1 Integer, found " + el.getTagAsString());
                }
                intCount++;
            }

            BigInteger version = ((DERInteger) seq[0]).asBigInteger();
            BigInteger modulus = ((DERInteger) seq[1]).asBigInteger();
            BigInteger publicExponent = ((DERInteger) seq[2]).asBigInteger();
            BigInteger privateExponent = ((DERInteger) seq[3]).asBigInteger();
            BigInteger primeP = ((DERInteger) seq[4]).asBigInteger();
            BigInteger primeQ = ((DERInteger) seq[5]).asBigInteger();
            BigInteger primeExponentP = ((DERInteger) seq[6]).asBigInteger();
            BigInteger primeExponentQ = ((DERInteger) seq[7]).asBigInteger();
            BigInteger crtCoefficient = ((DERInteger) seq[8]).asBigInteger();

            return new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent, primeP, primeQ,
                primeExponentP, primeExponentQ, crtCoefficient);
        } else if (cryptoStructure.hint == StructureHint.DSA_PRIVATE_KEY) {
        } else if (cryptoStructure.hint == StructureHint.EC_PRIVATE_KEY) {
        } else if (cryptoStructure.hint == StructureHint.PKCS8_PRIVATE_KEY) {
            // https://www.rfc-editor.org/rfc/rfc5208.html#section-5
            // we want to read java.security.spec.PKCS8EncodedKeySpec
            // Looking for this structure:
            //   PrivateKeyInfo ::= SEQUENCE {
            //     version                   Version,
            //     privateKeyAlgorithm       PrivateKeyAlgorithmIdentifier,
            //     privateKey                PrivateKey,
            //     attributes           [0]  IMPLICIT Attributes OPTIONAL }
            //   Version ::= INTEGER
            //   PrivateKeyAlgorithmIdentifier ::= AlgorithmIdentifier
            //   PrivateKey ::= OCTET STRING
            //   Attributes ::= SET OF Attribute
            if (seq.length >= 3 && seq[0] instanceof DERInteger version && seq[1] instanceof DERSequence && seq[2] instanceof DEROctetString) {
                // we should be fine
                if (version.asInt() != 0) {
                    throw new IllegalArgumentException("PKCS#8 Private Key with unsupported version: 0x" + version.asBigInteger().toString(16));
                }
                return new PKCS8EncodedKeySpec(cryptoStructure.derData);
            } else {
                throw new IllegalArgumentException("Unrecognized ASN.1 Structure for PKCS#8 Private Key");
            }
        } else if (cryptoStructure.hint == StructureHint.PKCS8_ENCRYPTED_PRIVATE_KEY) {
            // https://www.rfc-editor.org/rfc/rfc5208.html#section-6
            // Looking for this structure (requires decryption based on PBE algorithm):
            //  EncryptedPrivateKeyInfo ::= SEQUENCE {
            //    encryptionAlgorithm  EncryptionAlgorithmIdentifier,
            //    encryptedData        EncryptedData }
            //  EncryptionAlgorithmIdentifier ::= AlgorithmIdentifier
            //  EncryptedData ::= OCTET STRING
            //
            // Smallrye has limited support: https://github.com/smallrye/smallrye-certificate-generator/blob/ae9a2eed65912b8d33db9d0f90f644120a1da07e/private-key-pem-parser/src/main/java/io/smallrye/certs/pem/parsers/EncryptedPKCS8Parser.java#L75-L95
            //
            // Depending on the encryptionAlgorithm we may have PBES1 or PBES2 (newer)
            if (seq.length != 2 || !(seq[0] instanceof DERSequence alg && seq[1] instanceof DEROctetString data)) {
                throw new IllegalArgumentException("Unrecognized ASN.1 Structure for PKCS#8 Encrypted Private Key");
            }

            if (alg.getValues().length < 1 || !(alg.getValues()[0] instanceof DERObjectIdentifier oid)) {
                throw new IllegalArgumentException("Unrecognized PKCS#5 algorithm for PKCS#8 Encrypted Private Key");
            }

            if (DERObjectIdentifier.OID_PKCS5_PBES2.equals(oid.asOid())) {
                // PBES2 - PKCS#5: https://www.rfc-editor.org/rfc/rfc8018.html#section-6.2
                // structure like:
                // SEQUENCE
                //   SEQUENCE
                //   | OBJECT            :PBES2
                //   | SEQUENCE
                //   |   SEQUENCE
                //   |   | OBJECT            :PBKDF2
                //   |   | SEQUENCE
                //   |   |   OCTET STRING      [HEX DUMP]:2B10BFC176C442244C7...
                //   |   |   INTEGER           :0D
                //   |   |   INTEGER           :20
                //   |   |   SEQUENCE
                //   |   |     OBJECT            :hmacWithSHA512
                //   |   |     NULL
                //   |   SEQUENCE
                //   |     OBJECT            :aes-256-cbc
                //   |     OCTET STRING      [HEX DUMP]:00CA0E3DEC864877C6...
                //   OCTET STRING      [HEX DUMP]:159C11D5B6A114FE7C2FB736...

                // from Java perspective, we require PBKDF2 next
                //
                // supported PBKDF2 algorithms:
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
                if (alg.getValues().length < 2 || !(alg.getValues()[1] instanceof DERSequence kdfAndEnc)) {
                    throw new IllegalArgumentException("Unrecognized PKCS#5 PBES2 structure for PKCS#8 Encrypted Private Key");
                }
                DERObject[] seq2 = kdfAndEnc.getValues();
                if (seq2.length != 2 || !(seq2[0] instanceof DERSequence kdf && seq2[1] instanceof DERSequence encScheme)) {
                    throw new IllegalArgumentException("Unrecognized PKCS#5 PBES2 structure for PKCS#8 Encrypted Private Key");
                }
                if (kdf.getValues().length < 2 || !(kdf.getValues()[0] instanceof DERObjectIdentifier kdfOid && kdf.getValues()[1] instanceof DERSequence kdfParams)) {
                    throw new IllegalArgumentException("Unrecognized PKCS#5 PBES2 KDF structure for PKCS#8 Encrypted Private Key");
                }
                if (encScheme.getValues().length < 2 || !(encScheme.getValues()[0] instanceof DERObjectIdentifier encSchemeOid)) {
                    throw new IllegalArgumentException("Unrecognized PKCS#5 PBES2 Encryption Scheme structure for PKCS#8 Encrypted Private Key");
                }
                if (!DERObjectIdentifier.SUPPORTED_PBES2_KDFS.contains(kdfOid.asOid())) {
                    throw new IllegalArgumentException("Unsupported PKCS#5 PBES2 KDF algorithm: " + kdfOid.asOid());
                }
                if (!DERObjectIdentifier.SUPPORTED_PBES2_CIPHERS.containsKey(encSchemeOid.asOid())) {
                    throw new IllegalArgumentException("Unsupported PKCS#5 PBES2 Encryption Scheme algorithm: " + encSchemeOid.asOid());
                }
                // I think it's enough - remaining validation in javax.crypto.EncryptedPrivateKeyInfo
                try {
                    EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(cryptoStructure.derData());
                    // this is strange - epki.getAlgParameters().toString() gives us full algorithm name...
                    // while epki.getAlgParameters().getAlgorithm() gives is "PBES2"
//                    String algorithm = epki.getAlgParameters().toString();

                    // try to get KDF:
                    // PBKDF2-params ::= SEQUENCE {
                    //    salt CHOICE {
                    //        specified OCTET STRING,
                    //        otherSource AlgorithmIdentifier {{PBKDF2-SaltSources}}
                    //    },
                    //    iterationCount INTEGER (1..MAX),
                    //    keyLength INTEGER (1..MAX) OPTIONAL,
                    //    prf AlgorithmIdentifier {{PBKDF2-PRFs}} DEFAULT
                    //    algid-hmacWithSHA1 }
                    DERSequence prf = null;
                    if (kdfParams.getValues()[2] instanceof DERSequence) {
                        // skipped keyLength
                        prf = (DERSequence) kdfParams.getValues()[2];
                    } else if (kdfParams.getValues().length >= 4 && kdfParams.getValues()[3] instanceof DERSequence) {
                        prf = (DERSequence) kdfParams.getValues()[3];
                    }
                    if (prf == null || prf.getValues().length < 1 || !(prf.getValues()[0] instanceof DERObjectIdentifier prfOid)) {
                        throw new IllegalArgumentException("Unrecognized PKCS#5 PBES2 Pseudo-random function");
                    }
                    if (!DERObjectIdentifier.SUPPORTED_PBES2_PRFS.containsKey(prfOid.asOid())) {
                        throw new IllegalArgumentException("Unsupported PKCS#5 PBES2 Pseudo-random function: " + prfOid.asOid());
                    }

                    String algName = String.format("PBEWith%sAnd%s",
                        DERObjectIdentifier.SUPPORTED_PBES2_PRFS.get(prfOid.asOid()),
                        DERObjectIdentifier.SUPPORTED_PBES2_CIPHERS.get(encSchemeOid.asOid()));

                    // we only need password, here, PBEKeySpec with salt, ic and iv will be created internally by
                    // the PBE Cipher
                    PBEKeySpec spec = new PBEKeySpec(password == null ? new char[0] : password);

                    try {
                        SecretKeyFactory skf = SecretKeyFactory.getInstance(algName);
                        SecretKey sk = skf.generateSecret(spec);
                        Cipher pbeCipher = Cipher.getInstance(algName);
                        pbeCipher.init(Cipher.DECRYPT_MODE, sk, epki.getAlgParameters());
                        return epki.getKeySpec(pbeCipher);
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException |
                             InvalidKeyException | InvalidAlgorithmParameterException e) {
                        throw new IllegalArgumentException("Can't decrypt Private Key: " + e.getMessage(), e);
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Can't parse Private Key: " + e.getMessage(), e);
                }
            } else {
                // there's no OID for PBES1. It's like the structure nested in PBES2, but at the top level.
                // structure like:
                // SEQUENCE
                //   SEQUENCE
                //     OBJECT            :pbeWithSHA1And3-KeyTripleDES-CBC
                //     SEQUENCE
                //       OCTET STRING      [HEX DUMP]:C18E8024F41A7D64DA8FD0DB622...
                //       INTEGER           :0D
                //   OCTET STRING      [HEX DUMP]:4EA531CE3D2F4A7CE7D215F127DC6...
                //
                // supported PBKDF1 algorithms:
                //  - PBEWithMD5AndDES
                //  - PBEWithMD5AndTripleDES
                //  - PBEWithSHA1AndDESede
                //  - PBEWithSHA1AndRC2_128
                //  - PBEWithSHA1AndRC2_40
                //  - PBEWithSHA1AndRC4_128
                //  - PBEWithSHA1AndRC4_40
                if (alg.getValues().length < 2 || !(alg.getValues()[0] instanceof DERObjectIdentifier pbes1Oid && alg.getValues()[1] instanceof DERSequence)) {
                    throw new IllegalArgumentException("Unrecognized PKCS#5 structure for PKCS#8 Encrypted Private Key");
                }
                if (!DERObjectIdentifier.SUPPORTED_PBES1_KDFS.containsKey(pbes1Oid.asOid())) {
                    throw new IllegalArgumentException("Unsupported PKCS#5 PBES1 KDF algorithm: " + pbes1Oid.asOid());
                }
                try {
                    EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(cryptoStructure.derData());

                    String algName = DERObjectIdentifier.SUPPORTED_PBES1_KDFS.get(pbes1Oid.asOid());

                    // we only need password, here, PBEKeySpec with salt, ic and iv will be created internally by
                    // the PBE Cipher
                    PBEKeySpec spec = new PBEKeySpec(password == null ? new char[0] : password);

                    try {
                        SecretKeyFactory skf = SecretKeyFactory.getInstance(algName);
                        SecretKey sk = skf.generateSecret(spec);
                        Cipher pbeCipher = Cipher.getInstance(algName);
                        pbeCipher.init(Cipher.DECRYPT_MODE, sk, epki.getAlgParameters());
                        return epki.getKeySpec(pbeCipher);
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException |
                             InvalidKeyException | InvalidAlgorithmParameterException e) {
                        throw new IllegalArgumentException("Can't decrypt Private Key: " + e.getMessage(), e);
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Can't parse Private Key: " + e.getMessage(), e);
                }
            }
        }

        if (cryptoStructure.hint == StructureHint.DER || !firstCheck) {
            throw new IllegalArgumentException("Can't read private key from ASN.1 structure");
        } else {
            throw new IllegalArgumentException("Can't handle private keys in \"" + cryptoStructure.pemMarker + "\" format");
        }
    }

    /**
     * Write DER data using PEM encoding and selected <em>marker</em>
     * @param marker
     * @param derData
     * @return
     */
    public static byte[] encodePem(String marker, byte[] derData) {
        String data =
            String.format("-----BEGIN %s-----\n", marker) +
            Base64.getMimeEncoder(64, new byte[]{0x0a}).encodeToString(derData) +
            String.format("\n-----END %s-----\n", marker);

        return data.getBytes(StandardCharsets.UTF_8);
    }

    // Originally this method was inspired and partly taken over from http://oauth.googlecode.com/svn/code/java/
    // All credits to belong to them.
    public static CryptoStructure decodePemIfNeeded(File pemFile) throws IOException {
        try (PushbackInputStream is = new PushbackInputStream(new FileInputStream(pemFile))) {
            int v = is.read();
            boolean soundsLikeDer = v == DERSequence.DER_SEQUENCE_TAG;
            is.unread(v);
            if (soundsLikeDer) {
                // let's assume it's a DER sequence
                return new CryptoStructure(StructureHint.DER, null, is.readAllBytes());
            } else {
                // let's treat it (failing if the assumption is wrong) as PEM data
                // will be closed with the wrapping try-with-resources
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line = reader.readLine();
                if (line.startsWith("-----BEGIN ") && line.endsWith("-----")) {
                    // should be PEM
                    String what = line.substring("-----BEGIN ".length(), line.length() - 5);
                    StructureHint hint = StructureHint.from(what);
                    if (hint == StructureHint.UNSUPPORTED_PEM) {
                        return new CryptoStructure(StructureHint.UNSUPPORTED_PEM, what, null);
                    } else {
                        byte[] bytes = readBytes(pemFile, reader, line.trim().replace("BEGIN", "END"));
                        return new CryptoStructure(hint, what, bytes);
                    }
                } else {
                    // no DER SEQUENCE, no PEM markers
                    throw new IOException(pemFile + " is neither a proper PEM file nor a ASN.1/DER SEQUENCE");
                }
            }
        }
    }

    private static byte[] readBytes(File pemFile, BufferedReader reader, String endMarker) throws IOException {
        String line;
        StringBuilder buf = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            if (line.equals(endMarker)) {
                return Base64.getMimeDecoder().decode(buf.toString());
            }
            buf.append(line.trim());
        }
        throw new IOException(pemFile + " is invalid : No end marker");
    }

    /**
     * Possible/supported algorithms we detect in ASN.1 structures
     */
    public enum StructureHint {
        /** Originally a DER format, so no PEM header at all */
        DER,

        /** Contains {@code ----BEGIN }, but is invalid (no end marker, or wrong delimiters) */
        INVALID_PEM,
        /** Contains {@code ----BEGIN } and proper markers, but the type is unsupported (like {@code BEGIN SSH2 PUBLIC KEY}) */
        UNSUPPORTED_PEM,

        /** X.509 SubjectPublicKeyInfo - {@code -----BEGIN PUBLIC KEY-----} */
        X509_SUBJECT_PUBLIC_KEY_INFO,

        /** PKCS#1 RSA Public Key - {@code -----BEGIN RSA PUBLIC KEY-----} */
        RSA_PUBLIC_KEY,
        /** PKCS#1 RSA Private Key - {@code -----BEGIN RSA PRIVATE KEY-----} */
        RSA_PRIVATE_KEY,

        /** DSA Private Key - no idea where it's specified - {@code -----BEGIN DSA PRIVATE KEY-----} */
        DSA_PRIVATE_KEY,

        /** ECPrivateKey from RFC 5915 - {@code -----BEGIN EC PRIVATE KEY-----} */
        EC_PRIVATE_KEY,

        /** PKCS#8 PrivateKeyInfo - {@code -----BEGIN PRIVATE KEY-----} */
        PKCS8_PRIVATE_KEY,
        /** PKCS#8 EncryptedPrivateKeyInfo - {@code -----BEGIN ENCRYPTED PRIVATE KEY-----} */
        PKCS8_ENCRYPTED_PRIVATE_KEY,

        /** X.509 Certificate - {@code -----BEGIN CERTIFICATE-----} */
        X509_CERTIFICATE;

        public static StructureHint from(String what) {
            return switch (what) {
                case "PUBLIC KEY" -> X509_SUBJECT_PUBLIC_KEY_INFO;
                case "RSA PUBLIC KEY" -> RSA_PUBLIC_KEY;
                case "RSA PRIVATE KEY" -> RSA_PRIVATE_KEY;
                case "DSA PRIVATE KEY" -> DSA_PRIVATE_KEY;
                case "EC PRIVATE KEY" -> EC_PRIVATE_KEY;
                case "PRIVATE KEY" -> PKCS8_PRIVATE_KEY;
                case "ENCRYPTED PRIVATE KEY" -> PKCS8_ENCRYPTED_PRIVATE_KEY;
                case "CERTIFICATE" -> X509_CERTIFICATE;
                default -> UNSUPPORTED_PEM;
            };
        }
    }

    /**
     * A wrapper of DER binary data and a hint from its source (like PEM headers)
     * @param hint
     * @param pemMarker
     * @param derData
     */
    public record CryptoStructure(StructureHint hint, String pemMarker, byte[] derData) {

        /**
         * Create new {@link CryptoStructure} with changed hint (and cleared pemMarker)
         * @param hint
         * @return
         */
        public CryptoStructure withNewHint(StructureHint hint) {
            return new CryptoStructure(hint, null, this.derData);
        }
    }

}
