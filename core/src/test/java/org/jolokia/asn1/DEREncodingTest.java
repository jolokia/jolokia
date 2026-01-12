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

import org.jolokia.core.util.KeyGenerationTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.StringWriter;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HexFormat;
import java.util.TimeZone;

import static org.testng.Assert.*;

public class DEREncodingTest {

    @Test
    public void encodeLength() {
        HexFormat hex = HexFormat.of().withUpperCase();
        Assert.assertEquals(hex.formatHex(org.jolokia.asn1.DERUtils.encodeLength(0x7F)), "7F");
        Assert.assertEquals(hex.formatHex(org.jolokia.asn1.DERUtils.encodeLength(0x80)), "8180");
        Assert.assertEquals(hex.formatHex(org.jolokia.asn1.DERUtils.encodeLength(0xFF)), "81FF");
        Assert.assertEquals(hex.formatHex(org.jolokia.asn1.DERUtils.encodeLength(0x100)), "820100");
        Assert.assertEquals(hex.formatHex(org.jolokia.asn1.DERUtils.encodeLength(Integer.MAX_VALUE)), "847FFFFFFF");
        try {
            org.jolokia.asn1.DERUtils.encodeLength(0x80000000);
            fail("Should fail for negative length");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void decodeLength() {
        HexFormat hex = HexFormat.of().withUpperCase();
        assertEquals(org.jolokia.asn1.DERUtils.decodeLength(hex.parseHex("7F")).length(), 0x7F);
        assertEquals(org.jolokia.asn1.DERUtils.decodeLength(hex.parseHex("8180")).length(), 0x80);
        assertEquals(org.jolokia.asn1.DERUtils.decodeLength(hex.parseHex("81FF")).length(), 0xFF);
        assertEquals(org.jolokia.asn1.DERUtils.decodeLength(hex.parseHex("820100")).length(), 0x100);
        assertEquals(org.jolokia.asn1.DERUtils.decodeLength(hex.parseHex("83010203")).length(), 0x010203);
        assertEquals(org.jolokia.asn1.DERUtils.decodeLength(hex.parseHex("847FFFFFFF")).length(), Integer.MAX_VALUE);
        try {
            org.jolokia.asn1.DERUtils.decodeLength(HexUtil.decode("850100000000"));
            fail("Should fail for too many length encoding octects");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            org.jolokia.asn1.DERUtils.decodeLength(HexUtil.decode("8480000000"));
            fail("Should fail for too long value");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            org.jolokia.asn1.DERUtils.decodeLength(HexUtil.decode("84000000"));
            fail("Should fail for not enough encoding length");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void encodeInteger() {
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(0).getEncoded()), "020100");

        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(1).getEncoded()), "020101");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(13).getEncoded()), "02010D");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(127).getEncoded()), "02017F");

        // -1 = 127 - 128 -> 127 | 128 = 255 = 0xFF
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-1).getEncoded()), "0201FF");
        // -13 = 115 - 128 -> 115 | 128 = 243 = 0xF3
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-13).getEncoded()), "0201F3");
        // -128 = 0 - 128 -> 0 | 128 = 128 = 0x80
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-128).getEncoded()), "020180");

        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(128).getEncoded()), "02020080");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(256).getEncoded()), "02020100");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(32767).getEncoded()), "02027FFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-129).getEncoded()), "0202FF7F");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-256).getEncoded()), "0202FF00");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-32768).getEncoded()), "02028000");

        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(32768).getEncoded()), "0203008000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(65536).getEncoded()), "0203010000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(8388607).getEncoded()), "02037FFFFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-32769).getEncoded()), "0203FF7FFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-65536).getEncoded()), "0203FF0000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-8388608).getEncoded()), "0203800000");

        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(8388608).getEncoded()), "020400800000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(16777216).getEncoded()), "020401000000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(2147483647).getEncoded()), "02047FFFFFFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(Integer.MAX_VALUE).getEncoded()), "02047FFFFFFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-8388609).getEncoded()), "0204FF7FFFFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(-16777216).getEncoded()), "0204FF000000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(Integer.MIN_VALUE).getEncoded()), "020480000000");

        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(BigInteger.ZERO).getEncoded()), "020100");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(BigInteger.ONE).getEncoded()), "020101");
        assertEquals(new BigInteger("80", 16).intValue(), 128);
        assertEquals(new BigInteger("-80", 16).intValue(), -128);
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("-80", 16)).getEncoded()), "020180");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("80", 16)).getEncoded()), "02020080");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("7FFF", 16)).getEncoded()), "02027FFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("FF7FFF", 16)).getEncoded()), "020400FF7FFF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("-FF7FFF", 16)).getEncoded()), "0204FF008001");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("FF000000", 16)).getEncoded()), "020500FF000000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("-FF000000", 16)).getEncoded()), "0205FF01000000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERInteger(new BigInteger("0102030405060708090a", 16)).getEncoded()), "020A0102030405060708090A");
    }

    @Test
    public void decodeInteger() {
        assertEquals(((org.jolokia.asn1.DERInteger) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("02017F"))).asInt(), 127);
        assertEquals(((org.jolokia.asn1.DERInteger) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("0201FF"))).asInt(), -1);
        assertEquals(((org.jolokia.asn1.DERInteger) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("020180"))).asInt(), -128);
        assertEquals(((org.jolokia.asn1.DERInteger) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("02020080"))).asInt(), 128);
        assertEquals(((org.jolokia.asn1.DERInteger) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("0205FF01000000"))).asBigInteger(), new BigInteger("-FF000000", 16));
    }

    @Test
    public void encodeString() {
        Assert.assertEquals(HexUtil.encode(new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello!").getEncoded()), "130648656C6C6F21");
        Assert.assertEquals(HexUtil.encode(new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, "ąćęłńóśżź").getEncoded()), "0C12C485C487C499C582C584C3B3C59BC5BCC5BA");
        Assert.assertEquals(HexUtil.encode(new DEROctetString(DEROctetString.DER_OCTETSTRING_TAG, "ąćęłńóśżź").getEncoded()), "0412C485C487C499C582C584C3B3C59BC5BCC5BA");
        Assert.assertEquals(HexUtil.encode(new DEROctetString(DEROctetString.DER_IA5STRING_TAG, "ąćęłńóśżź").getEncoded()), "1612C485C487C499C582C584C3B3C59BC5BCC5BA");
        StringWriter sw = new StringWriter();
        for (int i = 0; i < 127; i++) {
            sw.append("A");
        }
        assertTrue(HexUtil.encode(new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, sw.toString()).getEncoded()).startsWith("0C7F41"));
        sw = new StringWriter();
        for (int i = 0; i < 128; i++) {
            sw.append("A");
        }
        assertTrue(HexUtil.encode(new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, sw.toString()).getEncoded()).startsWith("0C818041"));
        sw = new StringWriter();
        for (int i = 0; i < 255; i++) {
            sw.append("A");
        }
        assertTrue(HexUtil.encode(new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, sw.toString()).getEncoded()).startsWith("0C81FF41"));
        sw = new StringWriter();
        for (int i = 0; i < 257; i++) {
            sw.append("A");
        }
        assertTrue(HexUtil.encode(new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, sw.toString()).getEncoded()).startsWith("0C82010141"));
    }

    @Test
    public void encodeUtcTime() throws ParseException {
        DateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DF.setTimeZone(TimeZone.getTimeZone("CET"));

        Assert.assertEquals(HexUtil.encode(new DERUtcTime(DF.parse("2021-06-25 10:11:12")).getEncoded()), "170D3231303632353038313131325A");
    }

    @Test
    public void encodeBoolean() {
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERBoolean(true).getEncoded()), "0101FF");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERBoolean(false).getEncoded()), "010100");
    }

    @Test
    public void decodeBoolean() {
        assertTrue(((org.jolokia.asn1.DERBoolean) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("0101FF"))).getValue());
        assertTrue(((org.jolokia.asn1.DERBoolean) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("010142"))).getValue());
        assertFalse(((org.jolokia.asn1.DERBoolean) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("010100"))).getValue());
    }

    @Test
    public void encodeNull() {
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERNull().getEncoded()), "0500");
    }

    @Test
    public void decodeNull() {
        assertTrue(org.jolokia.asn1.DERUtils.parse(HexUtil.decode("0500")) instanceof org.jolokia.asn1.DERNull);
    }

    @Test
    public void encodeOid() {
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("1").getEncoded()), "060128");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("1.0").getEncoded()), "060128");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("1.1").getEncoded()), "060129");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("2.56").getEncoded()), "06028108");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("2.999.3").getEncoded()), "0603883703");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("2.5.4.8").getEncoded()), "0603550408");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier("1.2.3.4.5.6").getEncoded()), "06052A03040506");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier(org.jolokia.asn1.DERObjectIdentifier.OID_rsaEncryption).getEncoded()), "06092A864886F70D010101");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERObjectIdentifier(org.jolokia.asn1.DERObjectIdentifier.OID_sha1WithRSAEncryption).getEncoded()), "06092A864886F70D010105");
    }

    @Test
    public void decodeOid() {
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("060128"))).asOid(), "1.0");
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("060129"))).asOid(), "1.1");
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("06017F"))).asOid(), "2.47");

        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("06028108"))).asOid(), "2.56");
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("0603883703"))).asOid(), "2.999.3");
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("0603550408"))).asOid(), "2.5.4.8");
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("06052A03040506"))).asOid(), "1.2.3.4.5.6");

        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("06092A864886F70D010101"))).asOid(), org.jolokia.asn1.DERObjectIdentifier.OID_rsaEncryption);
        assertEquals(((org.jolokia.asn1.DERObjectIdentifier) org.jolokia.asn1.DERUtils.parse(HexUtil.decode("06092A864886F70D010105"))).asOid(), org.jolokia.asn1.DERObjectIdentifier.OID_sha1WithRSAEncryption);
    }

    @Test
    public void encodeSequence() {
        org.jolokia.asn1.DERSequence seq = new org.jolokia.asn1.DERSequence(new DERObject[] {
                new org.jolokia.asn1.DERInteger(42),
                new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello")
        });
        Assert.assertEquals(HexUtil.encode(seq.getEncoded()), "300A02012A130548656C6C6F");
    }

    @Test
    public void encodeSet() {
        org.jolokia.asn1.DERSet seq = new org.jolokia.asn1.DERSet(new DERObject[] {
                new org.jolokia.asn1.DERInteger(42),
                new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello")
        });
        Assert.assertEquals(HexUtil.encode(seq.getEncoded()), "310A02012A130548656C6C6F");
    }

    @Test
    public void encodeTaggedObject() {
        org.jolokia.asn1.DERTaggedObject object = new org.jolokia.asn1.DERTaggedObject(org.jolokia.asn1.DERTaggedObject.TagClass.ContextSpecific, false, (byte) 0, new org.jolokia.asn1.DERInteger(42));
        Assert.assertEquals(HexUtil.encode(object.getEncoded()), "A00302012A");
    }

    @Test
    public void encodeExplicitValue() {
        DERContextSpecific v = new DERContextSpecific((byte) 30, DERContextSpecific.TagMode.EXPLICIT, false, new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, "Hello"));
        KeyGenerationTest.printDer(v, 0);
        System.out.println(HexUtil.encode(v.getEncoded()));
    }

    @Test
    public void encodeImplicitValue() {
        DERContextSpecific v = new DERContextSpecific((byte) 30, DERContextSpecific.TagMode.IMPLICIT, true, new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, "Hello"));
        KeyGenerationTest.printDer(v, 0);
        System.out.println(HexUtil.encode(v.getEncoded()));
    }

    @Test
    public void encodeBitString() {
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERBitString(new byte[0]).getEncoded()), "030100");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERBitString(new byte[] { 0x00 }).getEncoded()), "03020000");
        Assert.assertEquals(HexUtil.encode(new org.jolokia.asn1.DERBitString(new byte[] { (byte) 0xF0 }).getEncoded()), "030200F0");

        // $ echo -n 301D02012B031800301502012A06092A864886F70D010105130548656C6C6F | xxd -p -r | openssl asn1parse -inform der -i
        //     0:d=0  hl=2 l=  29 cons: SEQUENCE
        //     2:d=1  hl=2 l=   1 prim:  INTEGER           :2B
        //     5:d=1  hl=2 l=  24 prim:  BIT STRING
        // $ echo -n 301D02012B031800301502012A06092A864886F70D010105130548656C6C6F | xxd -p -r | openssl asn1parse -inform der -i -strparse 5
        //     0:d=0  hl=2 l=  21 cons: SEQUENCE
        //     2:d=1  hl=2 l=   1 prim:  INTEGER           :2A
        //     5:d=1  hl=2 l=   9 prim:  OBJECT            :sha1WithRSAEncryption
        //    16:d=1  hl=2 l=   5 prim:  PRINTABLESTRING   :Hello

        org.jolokia.asn1.DERSequence seq1 = new org.jolokia.asn1.DERSequence(new DERObject[] {
                new org.jolokia.asn1.DERInteger(42),
                new org.jolokia.asn1.DERObjectIdentifier(org.jolokia.asn1.DERObjectIdentifier.OID_sha1WithRSAEncryption),
                new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello")
        });
        org.jolokia.asn1.DERSequence seq2 = new org.jolokia.asn1.DERSequence(new DERObject[] {
                new org.jolokia.asn1.DERInteger(43),
                new org.jolokia.asn1.DERBitString(seq1.getEncoded())
        });
        Assert.assertEquals(HexUtil.encode(seq2.getEncoded()), "301D02012B031800301502012A06092A864886F70D010105130548656C6C6F");
    }

}
