package org.jolokia.jvmagent.security;/*
 *
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;

import org.jolokia.jvmagent.security.asn1.DERBitString;
import org.jolokia.jvmagent.security.asn1.DERInteger;
import org.jolokia.jvmagent.security.asn1.DERObject;
import org.jolokia.jvmagent.security.asn1.DERObjectIdentifier;
import org.jolokia.jvmagent.security.asn1.DERSequence;
import org.jolokia.jvmagent.security.asn1.DERUtils;
import org.jolokia.jvmagent.security.asn1.HexUtil;
import org.jolokia.server.core.util.Base64Util;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 01/10/15
 */
public class PKCS1UtilTest {

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
            "rxVfuwIDAQAB\n";

    public String PUBLIC_KEY =
            "MIIBCgKCAQEAos30ovwGS0x0aG8aDCGkyEBmGXQPf+nwJpAoezSHGPV+7GEBjv2D7EEuZw0jk1DW\n" +
            "z3Kdzj84ei23HOux0CAeB9G6ca2VeNrgN97vL0jPyt/j/Ovmd+f4sJi87+ks1hk4pIzCPSGT5p44\n" +
            "67gGRrTxbjWMRoNJfKw6VvJPRDIx3L/MOF/PXQZPQVSfHjmtNoD0K7hqj6iEp1XErsUsLHlMMr5y\n" +
            "oKCdILbQJvXmB0rxlZTBZIOVey/35Eg6M7nc0A5Ls2qipD03AiMkLUpucZasFUz9wlWdgMfuyX6m\n" +
            "WMzqIaF7CpBRQ7wWDXjtRD/TBuRa5Hikrjy2cznayLDfrxVfuwIDAQAB";

    @Test
    public void simple() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        RSAPrivateCrtKeySpec spec = PKCS1Util.decodePKCS1(Base64Util.decode(PRIVATE_KEY));
        assertNotNull(spec);
        assertEquals(spec.getPublicExponent(),
            new BigInteger("65537"));
        assertEquals(spec.getPrivateExponent(),
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
    public void rsaX509PublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = PKCS1Util.decodePKCS1PublicKey(Base64Util.decode(PUBLIC_KEY_X509));
        assertNotNull(spec);
        assertTrue(spec instanceof X509EncodedKeySpec);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
        assertEquals(((RSAPublicKey) publicKey).getModulus(), new BigInteger("A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB", 16));
        assertEquals(((RSAPublicKey) publicKey).getPublicExponent(), new BigInteger("10001", 16));

        DERObject seq = DERUtils.parse(Base64Util.decode(PUBLIC_KEY_X509));
        assertTrue(((DERSequence) seq).getValues()[0] instanceof DERSequence);
        DERObject oid = ((DERSequence) ((DERSequence) seq).getValues()[0]).getValues()[0];
        assertTrue(oid instanceof DERObjectIdentifier);
        assertEquals(((DERObjectIdentifier) oid).asOid(), DERObjectIdentifier.OID_rsaEncryption);
        DERObject bitString = ((DERSequence) seq).getValues()[1];
        assertTrue(bitString instanceof DERBitString);
        assertEquals(HexUtil.encode(((DERBitString) bitString).getValue()), "3082010A0282010100A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB0203010001");
    }

    @Test
    public void rsaPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = PKCS1Util.decodePKCS1PublicKey(Base64Util.decode(PUBLIC_KEY));
        assertNotNull(spec);
        assertTrue(spec instanceof RSAPublicKeySpec);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
        assertEquals(((RSAPublicKey) publicKey).getModulus(), new BigInteger("A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB", 16));
        assertEquals(((RSAPublicKey) publicKey).getPublicExponent(), new BigInteger("10001", 16));

        DERObject seq = DERUtils.parse(Base64Util.decode(PUBLIC_KEY));
        assertTrue(((DERSequence) seq).getValues()[0] instanceof DERInteger);
        assertTrue(((DERSequence) seq).getValues()[1] instanceof DERInteger);
        BigInteger modulus = ((DERInteger) ((DERSequence) seq).getValues()[0]).asBigInteger();
        BigInteger pubicExponent = ((DERInteger) ((DERSequence) seq).getValues()[1]).asBigInteger();
        assertEquals(modulus, new BigInteger("A2CDF4A2FC064B4C74686F1A0C21A4C8406619740F7FE9F02690287B348718F57EEC61018EFD83EC412E670D239350D6CF729DCE3F387A2DB71CEBB1D0201E07D1BA71AD9578DAE037DEEF2F48CFCADFE3FCEBE677E7F8B098BCEFE92CD61938A48CC23D2193E69E38EBB80646B4F16E358C4683497CAC3A56F24F443231DCBFCC385FCF5D064F41549F1E39AD3680F42BB86A8FA884A755C4AEC52C2C794C32BE72A0A09D20B6D026F5E6074AF19594C16483957B2FF7E4483A33B9DCD00E4BB36AA2A43D370223242D4A6E7196AC154CFDC2559D80C7EEC97EA658CCEA21A17B0A905143BC160D78ED443FD306E45AE478A4AE3CB67339DAC8B0DFAF155FBB", 16));
        assertEquals(pubicExponent, new BigInteger("10001", 16));
    }

}
