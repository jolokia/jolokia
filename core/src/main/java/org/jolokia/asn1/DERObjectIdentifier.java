/*
 * Copyright 2009-2021 Roland Huss
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

package org.jolokia.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DERObjectIdentifier implements DERObject {

    public static final byte DER_OBJECTIDENTIFIER_TAG = 0x06;

    // https://oid-base.com/get/2.5.4
    public static final String OID_countryName = "2.5.4.6";
    public static final String OID_stateOrProvinceName = "2.5.4.8";
    public static final String OID_localityName = "2.5.4.7";
    public static final String OID_organizationName = "2.5.4.10";
    public static final String OID_organizationalUnitName = "2.5.4.11";
    public static final String OID_commonName = "2.5.4.3";

    public static final String OID_rsaEncryption = "1.2.840.113549.1.1.1";
    public static final String OID_sha1WithRSAEncryption = "1.2.840.113549.1.1.5";
    public static final String OID_sha512WithRSAEncryption = "1.2.840.113549.1.1.13";
    // https://datatracker.ietf.org/doc/html/rfc9688#name-rsassa-pkcs1-v15-with-sha3
    public static final String OID_SHA3_512withRSA = "2.16.840.1.101.3.4.3.16";

    // X.509 certificate extensions

    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.2
    public static final String OID_subjectKeyIdentifier = "2.5.29.14";
    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.3
    public static final String OID_keyUsage = "2.5.29.15";
    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.6
    public static final String OID_subjectAltName = "2.5.29.17";
    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.9
    public static final String OID_basicConstraints = "2.5.29.19";
    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.1
    public static final String OID_authorityKeyIdentifier = "2.5.29.35";
    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.12
    public static final String OID_extKeyUsage = "2.5.29.37";

    // Extended Key Usage OIDs
    public static final String OID_id_kp_serverAuth = "1.3.6.1.5.5.7.3.1";
    public static final String OID_id_kp_clientAuth = "1.3.6.1.5.5.7.3.2";
    public static final String OID_id_kp_codeSigning = "1.3.6.1.5.5.7.3.3";
    public static final String OID_id_kp_emailProtection = "1.3.6.1.5.5.7.3.4";

    // RFC 5280 - OIDs for SubjectPublicKeyInfo.algorithm
    // RFC 5480 - Digital Signature Algorithm (DSA) subject public key
    public static final String OID_PublicKey_DSA = "1.2.840.10040.4.1";
    // RFC 8410, RFC 8418 - id-X25519
    public static final String OID_PublicKey_X25519 = "1.3.101.110";
    public static final String OID_PublicKey_XDH = OID_PublicKey_X25519;
    // RFC 8410, RFC 8420 - id-Ed25519
    public static final String OID_PublicKey_Ed25519 = "1.3.101.112";
    public static final String OID_PublicKey_EdDSA = OID_PublicKey_Ed25519;
    // RFC 8410, RFC 8418 - id-X448
    public static final String OID_PublicKey_X448 = "1.3.101.111";
    // PKCS#1 - rsaEncryption
    public static final String OID_PublicKey_RSA = "1.2.840.113549.1.1.1";
    // PKCS#3 - dhKeyAgreement
    public static final String OID_PublicKey_DiffieHellman = "1.2.840.113549.1.3.1";
    // RFC 3447, RFC 8017 - rsassa-pss
    public static final String OID_PublicKey_RSASSA_PSS = "1.2.840.113549.1.1.10";
    // RFC 8410, RFC 8420 - id-Ed448
    public static final String OID_PublicKey_Ed448 = "1.3.101.113";
    // RFC 3279, RFC 5480, RFC 5753 - ecPublicKey
    public static final String OID_PublicKey_EC = "1.2.840.10045.2.1";

    // PKCS#5 supported algorithms
    public static final String OID_PKCS5_PBES2 = "1.2.840.113549.1.5.13";
    public static final String OID_PKCS5_PBKDF2 = "1.2.840.113549.1.5.12";
    public static final String OID_PKCS5_SCRYPT = "1.3.6.1.4.1.11591.4.11"; // not supported in Java
    // PBKDF2-PRFs
    // hmacWithSHA1
    public static final String OID_PKCS5_PBKDF2_hmacWithSHA1 = "1.2.840.113549.2.7";
    // hmacWithSHA224
    public static final String OID_PKCS5_PBKDF2_hmacWithSHA224 = "1.2.840.113549.2.8";
    // hmacWithSHA256
    public static final String OID_PKCS5_PBKDF2_hmacWithSHA256 = "1.2.840.113549.2.9";
    // hmacWithSHA384
    public static final String OID_PKCS5_PBKDF2_hmacWithSHA384 = "1.2.840.113549.2.10";
    // hmacWithSHA512
    public static final String OID_PKCS5_PBKDF2_hmacWithSHA512 = "1.2.840.113549.2.11";
    // PBESS2 encryptionScheme
    // aes128-CBC-PAD
    public static final String OID_PKCS5_PBKDF2_aes128_CBC_PAD = "2.16.840.1.101.3.4.1.2";
    // aes256-CBC-PAD
    public static final String OID_PKCS5_PBKDF2_aes256_CBC_PAD = "2.16.840.1.101.3.4.1.42";
    // PBKDF1
    // pbeWithMD5AndDES-CBC
    public static final String OID_PKCS5_PBKDF1_pbeWithMD5AndDES_CBC = "1.2.840.113549.1.5.3";
    // pbeWithSHAAnd3-KeyTripleDES-CBC
    public static final String OID_PKCS5_PBKDF1_pbeWithSHAAnd3_KeyTripleDES_CBC = "1.2.840.113549.1.12.1.3";
    // pbeWithSHAAnd128BitRC2-CBC
    public static final String OID_PKCS5_PBKDF1_pbeWithSHAAnd128BitRC2_CBC = "1.2.840.113549.1.12.1.5";
    // pbeWithSHAAnd40BitRC2-CBC
    public static final String OID_PKCS5_PBKDF1_pbeWithSHAAnd40BitRC2_CBC = "1.2.840.113549.1.12.1.6";
    // pbeWithSHAAnd128BitRC4
    public static final String OID_PKCS5_PBKDF1_pbeWithSHAAnd128BitRC4 = "1.2.840.113549.1.12.1.1";
    // pbeWithSHAAnd40BitRC4
    public static final String OID_PKCS5_PBKDF1_pbeWithSHAAnd40BitRC4 = "1.2.840.113549.1.12.1.2";
    // sun.security.util.KnownOIDs for PKCS#5 - not aligned with Cipher.getInstance("PBEWith***")...
    // // PKCS5 1.2.840.113549.1.5.*
    //  PBEWithMD5AndDES("1.2.840.113549.1.5.3"),
    //  PBEWithMD5AndRC2("1.2.840.113549.1.5.6"),
    //  PBEWithSHA1AndDES("1.2.840.113549.1.5.10"),
    //  PBEWithSHA1AndRC2("1.2.840.113549.1.5.11"),
    //  PBKDF2WithHmacSHA1("1.2.840.113549.1.5.12"),
    //  PBES2("1.2.840.113549.1.5.13"),

    /**
     * Supported Public Key algorithms from {@code SubjectPublicKeyInfo} from X.509. In PEM format
     * all these algorithms are used in {@code SubjectPublicKeyInfo} structure labeled with
     * {@code -----BEGIN PUBLIC KEY-----}
     */
    public static final Set<String> SUPPORTED_X509_PUBLIC_KEYS = Set.of(
        OID_PublicKey_DSA,
        OID_PublicKey_X25519,
        OID_PublicKey_Ed25519,
        OID_PublicKey_X448,
        OID_PublicKey_RSA,
        OID_PublicKey_DiffieHellman,
        OID_PublicKey_RSASSA_PSS,
        OID_PublicKey_Ed448,
        OID_PublicKey_EC
    );

    /**
     * Short set of supported key-derivation functions for PBES2 (no support for "scrypt" for example)
     */
    public static final Set<String> SUPPORTED_PBES2_KDFS = Set.of(
        OID_PKCS5_PBKDF2
    );

    /**
     * Map of supported PBKDF2 pseudo-random functions with mappings to Java Mac algorithms
     */
    public static final Map<String, String> SUPPORTED_PBES2_PRFS = Map.of(
        OID_PKCS5_PBKDF2_hmacWithSHA1, "HmacSHA1",
        OID_PKCS5_PBKDF2_hmacWithSHA224, "HmacSHA224",
        OID_PKCS5_PBKDF2_hmacWithSHA256, "HmacSHA256",
        OID_PKCS5_PBKDF2_hmacWithSHA384, "HmacSHA384",
        OID_PKCS5_PBKDF2_hmacWithSHA512, "HmacSHA512"
    );

    /**
     * Map of supported PBES2 encryption schemes with mappings to Java cipher algorithms in full
     * {@code PBEWithXXXAndYYY} template
     */
    public static final Map<String, String> SUPPORTED_PBES2_CIPHERS = Map.of(
        OID_PKCS5_PBKDF2_aes128_CBC_PAD, "AES_128",
        OID_PKCS5_PBKDF2_aes256_CBC_PAD, "AES_256"
    );

    /**
     * Supported key-derivation functions for PBKDF1 (PBES1) and mapping to Java algorithms
     */
    public static final Map<String, String> SUPPORTED_PBES1_KDFS = Map.of(
        OID_PKCS5_PBKDF1_pbeWithMD5AndDES_CBC, "PBEWithMD5AndDES",
        OID_PKCS5_PBKDF1_pbeWithSHAAnd3_KeyTripleDES_CBC, "PBEWithSHA1AndDESede", // the only one recoverable by openssl...
        OID_PKCS5_PBKDF1_pbeWithSHAAnd128BitRC2_CBC, "PBEWithSHA1AndRC2_128",
        OID_PKCS5_PBKDF1_pbeWithSHAAnd40BitRC2_CBC, "PBEWithSHA1AndRC2_40",
        OID_PKCS5_PBKDF1_pbeWithSHAAnd128BitRC4, "PBEWithSHA1AndRC4_128",
        OID_PKCS5_PBKDF1_pbeWithSHAAnd40BitRC4, "PBEWithSHA1AndRC4_40"
    );

    private final int[] values;
    private final String value;

    public DERObjectIdentifier(String value) {
        this.value = value;
        String[] vt = value.split("\\.");
        values = new int[vt.length];
        int p = 0;
        for (String v : vt) {
            try {
                if (v.trim().isEmpty()) {
                    throw new IllegalArgumentException("Bad syntax for OID \"" + value + "\"");
                }
                values[p] = Integer.parseInt(v);
                if (values[p] < 0) {
                    throw new IllegalArgumentException("Subidentifiers of OID should be greater than 0");
                }
                if (p == 0 && values[p] > 2) {
                    throw new IllegalArgumentException("First subidentifier of OID should be 0, 1 or 2");
                }
                p++;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad syntax for OID \"" + value + "\"");
            }
        }
    }

    @Override
    public byte getTag() {
        return DER_OBJECTIDENTIFIER_TAG;
    }

    @Override
    public String getTagAsString() {
        return "OBJECTIDENTIFIER";
    }

    @Override
    public byte[] getEncoded() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(DER_OBJECTIDENTIFIER_TAG);
        baos.write(0); // length - calculated later

        try {
            baos.write(encodeSubIdentifier(values[0] * 40 + (values.length > 1 ? values[1] : 0)));
            for (int i = 2; i < values.length; i++) {
                baos.write(encodeSubIdentifier(values[i]));
            }
        } catch (IOException ignored) {
        }

        byte[] result = baos.toByteArray();
        result[1] = (byte) (result.length - 2);
        return result;
    }

    private byte[] encodeSubIdentifier(int value) {
        // X.690, 8.19.2
        //  - bit 8 of the last octet is zero
        //  - bit 8 of each preceding octet is one.
        //  - bits 7 to 1 of the octets in the series collectively encode the subidentifier.
        byte[] maxResult = new byte[5];
        int v = value;
        for (int i = 4; i >= 0; i--) {
            maxResult[i] = (byte) (v & 0x7F);
            if (i < 4) {
                maxResult[i] |= (byte) 0x80;
            }
            v >>>= 7;
        }
        int start;
        for (start = 0; start < 4; start++) {
            if ((maxResult[start] & 0x7F) != 0) {
                break;
            }
        }
        if (start == 0) {
            return maxResult;
        } else {
            byte[] result = new byte[5 - start];
            System.arraycopy(maxResult, start, result, 0, 5 - start);
            return result;
        }
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public String toString() {
        return asOid();
    }

    public int[] getValues() {
        return values;
    }

    public String asOid() {
        return value;
    }

    public static DERObject parse(byte[] encoded, int length, int offset) {
        if (length > 127) {
            // it means we expect length octet to be 0x01 - 0x7f
            throw new IllegalArgumentException("Can't decode ObjectIdentifier with content octets exceeding 127 bytes");
        }

        // v1 can be 0, 1 or 2
        int v = encoded[offset] & 0xff;
        if (length == 1 && (v & 0x80) == 0) {
            // two components in single octet - easy way
            int v1 = v < 120 ? v / 40 : 2;
            int v2 = v - (v1 * 40);
            return new DERObjectIdentifier(String.format("%d.%d", v1, v2));
        }

        List<Integer> values = new ArrayList<>();
        int pos = offset;
        int current = 0;
        while (pos < offset + length) {
            v = encoded[pos] & 0xff;
            current <<= 7;
            current |= v & 0x7f;
            if ((v & 0x80) == 0) {
                // last octet of the oid component
                if (!values.isEmpty()) {
                    values.add(current);
                } else {
                    // we need to add two oid components for the first subidentifier
                    int v1 = current < 120 ? current / 40 : 2;
                    int v2 = current - (v1 * 40);
                    values.add(v1);
                    values.add(v2);
                }
                current = 0;
            }
            pos++;
        }
        return new DERObjectIdentifier(values.stream().map(i -> Integer.toString(i)).collect(Collectors.joining(".")));
    }

}
