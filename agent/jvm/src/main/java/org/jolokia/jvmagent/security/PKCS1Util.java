package org.jolokia.jvmagent.security;/*
 *
 * Copyright 2015
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
import java.math.BigInteger;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.jolokia.jvmagent.security.asn1.DERInteger;
import org.jolokia.jvmagent.security.asn1.DERObject;
import org.jolokia.jvmagent.security.asn1.DERSequence;
import org.jolokia.jvmagent.security.asn1.DERUtils;

/**
 * This code is inspired and taken over from net.auth.core:oauth
 * (albeit in a highly stripped variation):
 * <p>
 * Source is from http://oauth.googlecode.com/svn/code/java/ which is licensed
 * under the APL (http://oauth.googlecode.com/svn/code/java/LICENSE.txt)
 * <p>
 * All credits go to the original author (zhang)
 *
 * @author roland
 * @since 30/09/15
 */
class PKCS1Util {

    private PKCS1Util() {
    }

    /**
     * Read encoded {@code RSAPublicKey} specified in
     * <a href="https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.1">RFC 8017</a> and return
     * {@link RSAPublicKeySpec} which can be used to recreate a {@link java.security.PublicKey}
     * @param keyBytes
     * @return
     */
    public static KeySpec decodePKCS1PublicKey(byte[] keyBytes) {
        DERSequence seq = (DERSequence) DERUtils.parse(keyBytes);

        // https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.1
        // RSAPublicKey ::= SEQUENCE {
        //     modulus           INTEGER,  -- n
        //     publicExponent    INTEGER   -- e
        // }
        //
        // or
        // https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.7
        // SubjectPublicKeyInfo  ::=  SEQUENCE  {
        //     algorithm            AlgorithmIdentifier,
        //     subjectPublicKey     BIT STRING
        // }

        DERObject v1 = seq.getValues()[0];
        DERObject v2 = seq.getValues()[1];
        if (v1 instanceof DERInteger && v2 instanceof DERInteger) {
            // RSAPublicKey
            return new RSAPublicKeySpec(((DERInteger) v1).asBigInteger(), ((DERInteger) v2).asBigInteger());
        } else {
            // SubjectPublicKeyInfo
            return new X509EncodedKeySpec(keyBytes);
        }
    }

    /**
     * Read encoded {@code RSAPrivateKey} specified in
     * <a href="https://datatracker.ietf.org/doc/html/rfc8017#appendix-A.1.2">RFC 8017</a> and return
     * {@link RSAPrivateCrtKeySpec} which can be used to recreate a {@link java.security.PrivateKey}
     * @param keyBytes
     * @return
     */
    public static RSAPrivateCrtKeySpec decodePKCS1(byte[] keyBytes) {
        DERSequence seq = (DERSequence) DERUtils.parse(keyBytes);
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
        BigInteger version = ((DERInteger) seq.getValues()[0]).asBigInteger();
        BigInteger modulus = ((DERInteger) seq.getValues()[1]).asBigInteger();
        BigInteger publicExponent = ((DERInteger) seq.getValues()[2]).asBigInteger();
        BigInteger privateExponent = ((DERInteger) seq.getValues()[3]).asBigInteger();
        BigInteger primeP = ((DERInteger) seq.getValues()[4]).asBigInteger();
        BigInteger primeQ = ((DERInteger) seq.getValues()[5]).asBigInteger();
        BigInteger primeExponentP = ((DERInteger) seq.getValues()[6]).asBigInteger();
        BigInteger primeExponentQ = ((DERInteger) seq.getValues()[7]).asBigInteger();
        BigInteger crtCoefficient = ((DERInteger) seq.getValues()[8]).asBigInteger();

        return new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent, primeP, primeQ,
            primeExponentP, primeExponentQ, crtCoefficient);
    }

}
