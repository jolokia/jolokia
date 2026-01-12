/*
 * Copyright 2014 Roland Huss
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
import java.io.FilenameFilter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.jolokia.asn1.DERBitString;
import org.jolokia.asn1.DERInteger;
import org.jolokia.asn1.DERObject;
import org.jolokia.asn1.DERObjectIdentifier;
import org.jolokia.asn1.DERSequence;
import org.jolokia.asn1.DERUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 01/10/15
 */
public class CryptoUtilTest {

    public String PRIVATE_KEY =
        "MIIEpQIBAAKCAQEA2N/GkK5d93FnqtPggmZqJYceXkfetsIKMA/BAFqmlHvGFKp6\n" +
        "5wHPZvjqYkbhL2uDO4DT94ZfDtd0KMkDdn64DkPTUxq3G8TnTFUh26RdRNMPyRcV\n" +
        "AEZLOz6c5MhwlLIyAaA8oSoddSFGkERgY3GhNNnMeoRkgdgcGRyez26q3kPHn21D\n" +
        "0wZb9Li8tS3QjUZ/urE7Jy+3ElAD3CJ4AmBLAoyCrca4TjNUCBZdhYBf91PguYCg\n" +
        "CowhciBrJfetFmEz98PaNdKm476EA8AL7kNpYzFcT4c7gQD6HFaJFKSZemeCKvxH\n" +
        "txaKoaWXgrARiORQIF8kt5XGChc7rbaghOICOQIDAQABAoIBAQCIZr4Zk0GQdqgP\n" +
        "/jCvc0CBl+kWvTcrVQFZVx85XMp2ix57MvoXvsC2cAnig9fvnjwsuYsXnFC/Ie1y\n" +
        "FXNzHKIgfrI0C5JtCbu7+7NO1KLAvcqo3DaeNJfujCPblOGR9D2VXjWj27wpRiN+\n" +
        "azMAeKA+gFmmGQypycVqWeDccCtRnMDFE8FVTqPbOpJDT0jLObGGOzaqk6D/1GGZ\n" +
        "hpdNj03ZDOE7xGHGj8C53r/Kp+pNnmbRbJDw/6i2PMJtQYyycBra85ucb+IaUXSt\n" +
        "8E4bfj6NI+Faln8irkiHY/oCz6astoaO1ZuqbxZBmvvMyxM1IHB2cRykoSILr99w\n" +
        "IlTD3GHdAoGBAPqS47nBfUiUIqmWcWKw1G6KNQIcH1dEBCOfSx1jKnbx4w6H9xej\n" +
        "g2e+3HGqyyBFe8utfPrcRnD43arEEwUrXjUwgc3ZlWBY0XzanX4Z0DkUm8iHHfJo\n" +
        "XD1TyLsfp01fbKdKlFW5BW03Q7FOERXlheGvQynQfUBL53yG0WPTdrnvAoGBAN2S\n" +
        "EI1Cqy8aUL3ESsJ/bKpVgLhTwjovQxqM/uYVN282jI9EFAw+PQdpHyFdCr1fJmpY\n" +
        "nW7aHPYQxlWFQEIcoWROBY76VJMpxjLR1LPHV9ezzD9LoFINEGlZu5ZnQasNgFpX\n" +
        "5KjpGdlfa0MzPjauqxr7mUfvnLHolNmwubG9Pk5XAoGBAMACx5aUepifS2CA9CoY\n" +
        "LvD132Dag/mvGSzi6ACA+Q1klgWQkvv+RLe/PdWsdzMni5GsQ9VH7oKrcdFlpt2T\n" +
        "OgGwRgej8B+AcCcorv7ucO0MqcOkJoKXDffAuFUMEHvt36jiMYDu4wWqD6lSlS0e\n" +
        "UNV8JA9qwFAA2kZGWTYR2SzpAoGAH4efj1qDXaqS/s4mDVNwtTSBorlYlEsRc3/I\n" +
        "7hjq0IqkqeZ4K93XdWyCH49L7fLSVqPRk2q6YFG2x4i0wjOsy8dGhzgcPOze5XBy\n" +
        "ojqlx24wjHlIkSSGx1cbmKWM9LhxIWoMgfTZ1tL7Qo7SNZnZg3d2MoRoefCs7eV2\n" +
        "J1LUwPUCgYEAi4tPG9rpYMpD2gQ8u3G3GSWo+8mZOZ4s8/126ozoL4Uuwc1GwJ4o\n" +
        "HQ1qb6Er+XpE+QpOP1A164pmWkKo90wuDWdgXma45veNVSCblHvppSKFGeRaPr3P\n" +
        "i+L8P99EaGlPVUrgpwLVFs03SKmiEVjqDDlXw2+Yiu+9xmW5Pesb1tA=";

    public String PUBLIC_KEY_X509 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAos30ovwGS0x0aG8aDCGkyEBmGXQPf+nw\n" +
        "JpAoezSHGPV+7GEBjv2D7EEuZw0jk1DWz3Kdzj84ei23HOux0CAeB9G6ca2VeNrgN97vL0jPyt/j\n" +
        "/Ovmd+f4sJi87+ks1hk4pIzCPSGT5p4467gGRrTxbjWMRoNJfKw6VvJPRDIx3L/MOF/PXQZPQVSf\n" +
        "HjmtNoD0K7hqj6iEp1XErsUsLHlMMr5yoKCdILbQJvXmB0rxlZTBZIOVey/35Eg6M7nc0A5Ls2qi\n" +
        "pD03AiMkLUpucZasFUz9wlWdgMfuyX6mWMzqIaF7CpBRQ7wWDXjtRD/TBuRa5Hikrjy2cznayLDf\n" +
        "rxVfuwIDAQAB";

    public String PUBLIC_KEY =
        "MIIBCgKCAQEAos30ovwGS0x0aG8aDCGkyEBmGXQPf+nwJpAoezSHGPV+7GEBjv2D7EEuZw0jk1DW\n" +
        "z3Kdzj84ei23HOux0CAeB9G6ca2VeNrgN97vL0jPyt/j/Ovmd+f4sJi87+ks1hk4pIzCPSGT5p44\n" +
        "67gGRrTxbjWMRoNJfKw6VvJPRDIx3L/MOF/PXQZPQVSfHjmtNoD0K7hqj6iEp1XErsUsLHlMMr5y\n" +
        "oKCdILbQJvXmB0rxlZTBZIOVey/35Eg6M7nc0A5Ls2qipD03AiMkLUpucZasFUz9wlWdgMfuyX6m\n" +
        "WMzqIaF7CpBRQ7wWDXjtRD/TBuRa5Hikrjy2cznayLDfrxVfuwIDAQAB";

    @Test
    public void simple() {
        KeySpec spec = CryptoUtil.decodePrivateKey(new CryptoUtil.CryptoStructure(CryptoUtil.StructureHint.DER, null, Base64.getMimeDecoder().decode(PRIVATE_KEY)), null);
        assertNotNull(spec);
        assertTrue(spec instanceof RSAPrivateCrtKeySpec);
        assertEquals(((RSAPrivateCrtKeySpec) spec).getPublicExponent(),
            new BigInteger("65537"));
        assertEquals(((RSAPrivateCrtKeySpec) spec).getPrivateExponent(),
            new BigInteger("17219073728753538877351436880713775379656064037529658955194218882887990854659277" +
                                        "14926866974743136717398950769494187526607437089900175320890853259761202632924910" +
                                        "04313716240382424077365899979237567821238335068353270396753262938612364085446101" +
                                        "06439976729028133029376341040827650031161824951684561808300496926367402762121624" +
                                        "84716729598919532254889795000011013630896579601633649087197314473243762156761264" +
                                        "28213494680134367573578684374434499094707511493610021020953064686646715594465961" +
                                        "57768682849057097693703094001987209424161987563379538988444327461934240605326845" +
                                        "582692396320855699767767177230708015442200422586240491997"));
    }

    @Test
    public void rsaX509PublicKeyFromData() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = CryptoUtil.decodePublicKey(new CryptoUtil.CryptoStructure(CryptoUtil.StructureHint.DER, null, Base64.getMimeDecoder().decode(PUBLIC_KEY_X509)));
        assertNotNull(spec);
        assertTrue(spec instanceof X509EncodedKeySpec);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
        assertEquals(((RSAPublicKey) publicKey).getModulus(), new BigInteger("A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB", 16));
        assertEquals(((RSAPublicKey) publicKey).getPublicExponent(), new BigInteger("10001", 16));

        DERObject seq = DERUtils.parse(Base64.getMimeDecoder().decode(PUBLIC_KEY_X509));
        assertTrue(((DERSequence) seq).getValues()[0] instanceof DERSequence);
        DERObject oid = ((DERSequence) ((DERSequence) seq).getValues()[0]).getValues()[0];
        assertTrue(oid instanceof DERObjectIdentifier);
        assertEquals(((DERObjectIdentifier) oid).asOid(), DERObjectIdentifier.OID_rsaEncryption);
        DERObject bitString = ((DERSequence) seq).getValues()[1];
        assertTrue(bitString instanceof DERBitString);
        assertEquals(HexFormat.of().withUpperCase().formatHex(((DERBitString) bitString).getValue()), "3082010A0282010100A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB0203010001");
    }

    @Test
    public void rsaPKCS1PublicKeyFromData() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = CryptoUtil.decodePublicKey(new CryptoUtil.CryptoStructure(CryptoUtil.StructureHint.DER, null, Base64.getMimeDecoder().decode(PUBLIC_KEY)));
        assertNotNull(spec);
        assertTrue(spec instanceof RSAPublicKeySpec);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
        assertEquals(((RSAPublicKey) publicKey).getModulus(), new BigInteger("A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB", 16));
        assertEquals(((RSAPublicKey) publicKey).getPublicExponent(), new BigInteger("10001", 16));

        DERObject seq = DERUtils.parse(Base64.getMimeDecoder().decode(PUBLIC_KEY));
        assertTrue(((DERSequence) seq).getValues()[0] instanceof DERInteger);
        assertTrue(((DERSequence) seq).getValues()[1] instanceof DERInteger);
        BigInteger modulus = ((DERInteger) ((DERSequence) seq).getValues()[0]).asBigInteger();
        BigInteger pubicExponent = ((DERInteger) ((DERSequence) seq).getValues()[1]).asBigInteger();
        assertEquals(modulus, new BigInteger("A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB", 16));
        assertEquals(pubicExponent, new BigInteger("10001", 16));
    }

    @Test
    public void rsaX509PublicKeyInPEMFormat() throws Exception {
        File file = new File("target/test-classes/publickeys/RSA-pub.pem");
        CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(file);
        assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.X509_SUBJECT_PUBLIC_KEY_INFO);
        KeySpec keySpec = CryptoUtil.decodePublicKey(cryptoData);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(keySpec);
        assertNotNull(publicKey);
        assertEquals(publicKey.getAlgorithm(), "RSA");
    }

    @Test
    public void rsaX509PublicKeyInDERFormat() throws Exception {
        File file = new File("target/test-classes/publickeys/RSA-pub.der");
        CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(file);
        assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
        KeySpec keySpec = CryptoUtil.decodePublicKey(cryptoData);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(keySpec);
        assertNotNull(publicKey);
        assertEquals(publicKey.getAlgorithm(), "RSA");
    }

    @Test
    public void rsaPKCS1PublicKeyInPEMFormat() throws Exception {
        File file = new File("target/test-classes/publickeys/RSA-pub-pkcs1.pem");
        CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(file);
        assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.RSA_PUBLIC_KEY);
        KeySpec keySpec = CryptoUtil.decodePublicKey(cryptoData);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(keySpec);
        assertNotNull(publicKey);
        assertEquals(publicKey.getAlgorithm(), "RSA");
    }

    @Test
    public void rsaPKCS1PublicKeyInDERFormat() throws Exception {
        File file = new File("target/test-classes/publickeys/RSA-pub-pkcs1.der");
        CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(file);
        assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
        KeySpec keySpec = CryptoUtil.decodePublicKey(cryptoData);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(keySpec);
        assertNotNull(publicKey);
        assertEquals(publicKey.getAlgorithm(), "RSA");
    }

    @Test
    public void allSupportedX509PublicKeysInPEMFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/publickeys");
        String[] pemEncodedPublicKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pub.pem");
            }
        });
        assertNotNull(pemEncodedPublicKeys);
        assertEquals(pemEncodedPublicKeys.length, 11);
        for (String pemPublicKey : pemEncodedPublicKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPublicKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.X509_SUBJECT_PUBLIC_KEY_INFO);
            String algorithm = pemPublicKey.replace("-pub.pem", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
            PublicKey publicKey = factory.generatePublic(CryptoUtil.decodePublicKey(cryptoData));
            assertNotNull(publicKey);
            assertEquals(publicKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));

            // checking OIDs of SubjectPublicKeyInfo
            byte[] data = cryptoData.derData();
            DERObject der = DERUtils.parse(data);
            if (!(der instanceof DERSequence derSequence)) {
                fail("Expected DER SEQUENCE");
                return;
            }
            assertEquals(derSequence.getValues().length, 2);
            DERObject der2 = derSequence.getValues()[0];
            if (!(der2 instanceof DERSequence derSequence2)) {
                fail("Expected DER SEQUENCE as first item of SubjectPublicKeyInfo");
                return;
            }
            assertTrue(derSequence2.getValues().length > 0);
            DERObject oidValue = derSequence2.getValues()[0];
            if (!(oidValue instanceof DERObjectIdentifier oid)) {
                fail("Expected AlgorithmIdentifier in SubjectPublicKeyInfo");
                return;
            }
            System.out.println(pemPublicKey + ": " + oid.asOid());
        }
    }

    @Test
    public void allSupportedX509PublicKeysInDERFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/publickeys");
        String[] pemEncodedPublicKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pub.der");
            }
        });
        assertNotNull(pemEncodedPublicKeys);
        assertEquals(pemEncodedPublicKeys.length, 11);
        for (String pemPublicKey : pemEncodedPublicKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPublicKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
            String algorithm = pemPublicKey.replace("-pub.der", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
            PublicKey publicKey = factory.generatePublic(CryptoUtil.decodePublicKey(cryptoData));
            assertNotNull(publicKey);
            assertEquals(publicKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));
        }
    }

    @Test
    public void allSupportedPKCS8NonEncryptedPrivateKeysInPEMFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/privatekeys");
        String[] pemEncodedPKCS8PrivateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pkcs8.pem");
            }
        });
        assertNotNull(pemEncodedPKCS8PrivateKeys);
        assertEquals(pemEncodedPKCS8PrivateKeys.length, 11);
        for (String pemPrivateKey : pemEncodedPKCS8PrivateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPrivateKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.PKCS8_PRIVATE_KEY);
            String algorithm = pemPrivateKey.replace("-pkcs8.pem", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
            PrivateKey privateKey = factory.generatePrivate(CryptoUtil.decodePrivateKey(cryptoData, null));
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));

            // checking OIDs
            byte[] data = cryptoData.derData();
            DERObject der = DERUtils.parse(data);
            if (!(der instanceof DERSequence derSequence)) {
                fail("Expected DER SEQUENCE");
                return;
            }
            assertTrue(derSequence.getValues().length >= 3);
            DERObject der2 = derSequence.getValues()[1];
            if (!(der2 instanceof DERSequence derSequence2)) {
                fail("Expected DER SEQUENCE as second item of PrivateKeyInfo");
                return;
            }
            assertTrue(derSequence2.getValues().length > 0);
            DERObject oidValue = derSequence2.getValues()[0];
            if (!(oidValue instanceof DERObjectIdentifier oid)) {
                fail("Expected PrivateKeyAlgorithmIdentifier in PrivateKeyInfo");
                return;
            }
            System.out.println(": " + oid.asOid());
        }
    }

    @Test
    public void allSupportedPKCS8NonEncryptedPrivateKeysInDERFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/privatekeys");
        String[] pemEncodedPKCS8PrivateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pkcs8.der");
            }
        });
        assertNotNull(pemEncodedPKCS8PrivateKeys);
        assertEquals(pemEncodedPKCS8PrivateKeys.length, 11);
        for (String pemPrivateKey : pemEncodedPKCS8PrivateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPrivateKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
            String algorithm = pemPrivateKey.replace("-pkcs8.der", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
            PrivateKey privateKey = factory.generatePrivate(CryptoUtil.decodePrivateKey(cryptoData, null));
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));
        }
    }

    @Test
    public void allSupportedPKCS8PBES2PrivateKeysInPEMFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/privatekeys");
        String[] pemEncodedPKCS8PrivateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pkcs8-pbes2.pem");
            }
        });
        assertNotNull(pemEncodedPKCS8PrivateKeys);
        assertEquals(pemEncodedPKCS8PrivateKeys.length, 11);
        for (String pemPrivateKey : pemEncodedPKCS8PrivateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPrivateKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.PKCS8_ENCRYPTED_PRIVATE_KEY);
            String algorithm = pemPrivateKey.replace("-PBEWithHmacSHA512AndAES_256-pkcs8-pbes2.pem", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
//            KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
            PrivateKey privateKey = factory.generatePrivate(CryptoUtil.decodePrivateKey(cryptoData, "jolokia".toCharArray()));
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));
        }
    }

    @Test
    public void allSupportedPKCS8PBES2PrivateKeysInDERFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/privatekeys");
        String[] pemEncodedPKCS8PrivateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pkcs8-pbes2.der");
            }
        });
        assertNotNull(pemEncodedPKCS8PrivateKeys);
        assertEquals(pemEncodedPKCS8PrivateKeys.length, 11);
        for (String pemPrivateKey : pemEncodedPKCS8PrivateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPrivateKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
            String algorithm = pemPrivateKey.replace("-PBEWithHmacSHA512AndAES_256-pkcs8-pbes2.der", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
//            KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
            PrivateKey privateKey = factory.generatePrivate(CryptoUtil.decodePrivateKey(cryptoData, "jolokia".toCharArray()));
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));
        }
    }

    @Test
    public void allSupportedPKCS8PBES1PrivateKeysInPEMFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/privatekeys");
        String[] pemEncodedPKCS8PrivateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pkcs8-pbes1.pem");
            }
        });
        assertNotNull(pemEncodedPKCS8PrivateKeys);
        assertEquals(pemEncodedPKCS8PrivateKeys.length, 11);
        for (String pemPrivateKey : pemEncodedPKCS8PrivateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPrivateKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.PKCS8_ENCRYPTED_PRIVATE_KEY);
            String algorithm = pemPrivateKey.replace("-PBEWithSHA1AndDESede-pkcs8-pbes1.pem", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
//            KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
            PrivateKey privateKey = factory.generatePrivate(CryptoUtil.decodePrivateKey(cryptoData, "jolokia".toCharArray()));
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));
        }
    }

    @Test
    public void allSupportedPKCS8PBES1PrivateKeysInDERFormat() throws Exception {
        Map<String, String> algMapping = Map.of(
            "X25519", "XDH",
            "X448", "XDH",
            "DiffieHellman", "DH",
            "Ed448", "EdDSA",
            "Ed25519", "EdDSA"
        );
        File dir = new File("target/test-classes/privatekeys");
        String[] pemEncodedPKCS8PrivateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pkcs8-pbes1.der");
            }
        });
        assertNotNull(pemEncodedPKCS8PrivateKeys);
        assertEquals(pemEncodedPKCS8PrivateKeys.length, 11);
        for (String pemPrivateKey : pemEncodedPKCS8PrivateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pemPrivateKey));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
            String algorithm = pemPrivateKey.replace("-PBEWithSHA1AndDESede-pkcs8-pbes1.der", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);
//            KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
            KeySpec spec = CryptoUtil.decodePrivateKey(cryptoData, "jolokia".toCharArray());
            assertTrue(spec instanceof PKCS8EncodedKeySpec);
            //assertEquals(algMapping.getOrDefault(((PKCS8EncodedKeySpec) spec).getAlgorithm(), algorithm), algorithm);
            PrivateKey privateKey = factory.generatePrivate(spec);
//            System.out.println(((PKCS8EncodedKeySpec) spec).getAlgorithm());
//            System.out.println(factory.getAlgorithm());
//            System.out.println(privateKey.getAlgorithm());
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algMapping.getOrDefault(algorithm, algorithm));
        }
    }

    @Test
    public void legacyNonEncryptedPrivateKeysInPEMFormat() throws Exception {
        File dir = new File("target/test-classes/legacyprivatekeys");
        String[] legacyKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-legacy.der");
            }
        });
        assertNotNull(legacyKeys);
        assertEquals(legacyKeys.length, 3);
        for (String key : legacyKeys) {
            System.out.println("Checking " + key);
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, key));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
            KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
            KeySpec spec = CryptoUtil.decodePrivateKey(cryptoData, "jolokia".toCharArray());
            String algorithmFromFile = key.replace("-legacy.der", "");
            String algorithmFromSpec = null;
            if (spec instanceof DSAPrivateKeySpec) {
                algorithmFromSpec = "DSA";
            } else if (spec instanceof RSAPrivateKeySpec) {
                algorithmFromSpec = "RSA";
            } else if (spec instanceof ECPrivateKeySpec) {
                algorithmFromSpec = "EC";
            } else {
                fail("Unexpected KeySpec class: " + spec.getClass().getName());
            }
            assertNotNull(algorithmFromSpec);
            assertEquals(algorithmFromSpec, algorithmFromFile);
            KeyFactory factory = KeyFactory.getInstance(algorithmFromSpec);
            PrivateKey privateKey = factory.generatePrivate(spec);
            assertNotNull(privateKey);
            assertEquals(privateKey.getAlgorithm(), algorithmFromSpec);
            if (spec instanceof DSAPrivateKeySpec) {
                assertEquals(((DSAPrivateKey) privateKey).getX(), ((DSAPrivateKeySpec) spec).getX());
            } else if (spec instanceof RSAPrivateKeySpec) {
                assertEquals(((RSAPrivateKey) privateKey).getPrivateExponent(), ((RSAPrivateKeySpec) spec).getPrivateExponent());
            } else /*if (spec instanceof ECPrivateKeySpec) */{
                // the privateKey.getEncoded() will be PKCS8, not "legacy" EC
                assertEquals(((ECPrivateKey) privateKey).getS(), ((ECPrivateKeySpec) spec).getS());
            }
        }
    }

    @Test
    public void derPrinting() throws Exception {
        CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File("target/test-classes/rsaencryptedkeys", "RSA-PBEWithSHA1AndDESede-pkcs8-pbes1.pem"));
        KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
    }

    @Test
    public void allSupportedPBEsForRSAEncryptedKeys() throws Exception {
        // Note: PBEWithHmacSHA* are PBES2, PBEWithMD5*/PBEWithSHA1* PBES1 (legacy)
        File dir = new File("target/test-classes/rsaencryptedkeys");
        String[] privateKeys = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("RSA-PBEWith");
            }
        });
        assertNotNull(privateKeys);
        assertEquals(privateKeys.length, 16);
        for (String pk : privateKeys) {
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir, pk));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.PKCS8_ENCRYPTED_PRIVATE_KEY);
            String algorithm = pk.replace("RSA-", "").replace("-pkcs8-pbes2.pem", "").replace("-pkcs8-pbes1.pem", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
//            KeyGenerationTest.printDer(DERUtils.parse(cryptoData.derData()), 0);
            PrivateKey privateKey = factory.generatePrivate(CryptoUtil.decodePrivateKey(cryptoData, "jolokia".toCharArray()));
            assertNotNull(privateKey);
        }
    }

    @Test
    public void matchingPrivateAndPublicKeys() throws Exception {
        File dir1 = new File("target/test-classes/publickeys");
        File dir2 = new File("target/test-classes/privatekeys");
        String[] publicKeys = dir1.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-pub.der");
            }
        });
        assertNotNull(publicKeys);
        assertEquals(publicKeys.length, 11);
        for (String pk : publicKeys) {
            System.out.println("Checking " + pk);
            CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(new File(dir1, pk));
            assertEquals(cryptoData.hint(), CryptoUtil.StructureHint.DER);
            String algorithm = pk.replace("-pub.der", "");
            KeyFactory factory = KeyFactory.getInstance(algorithm);

            KeySpec spec = CryptoUtil.decodePublicKey(cryptoData);
            assertTrue(spec instanceof X509EncodedKeySpec);
            PublicKey publicKey = factory.generatePublic(spec);

            CryptoUtil.CryptoStructure cryptoData2 = CryptoUtil.decodePemIfNeeded(new File(dir2, algorithm + "-pkcs8.pem"));
            assertEquals(cryptoData2.hint(), CryptoUtil.StructureHint.PKCS8_PRIVATE_KEY);

            KeySpec spec2 = CryptoUtil.decodePrivateKey(cryptoData2, null);
            assertTrue(spec2 instanceof PKCS8EncodedKeySpec);

            PrivateKey privateKey = factory.generatePrivate(spec2);

            if (Set.of("X448", "X25519", "XDH", "DiffieHellman").contains(algorithm)) {
                // https://www.rfc-editor.org/rfc/rfc7748.html#section-5
                assertFalse(CryptoUtil.keysMatch(privateKey, publicKey));
            } else {
                assertTrue(CryptoUtil.keysMatch(privateKey, publicKey));
            }
        }
    }

}
