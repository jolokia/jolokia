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

package org.jolokia.jvmagent.security.asn1;

import org.testng.annotations.Test;

import java.io.StringWriter;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static org.testng.Assert.*;

public class DEREncodingTest {

    @Test
    public void encodeLength() {
        assertEquals(HexUtil.encode(DERUtils.encodeLength(0x7F)), "7F");
        assertEquals(HexUtil.encode(DERUtils.encodeLength(0x80)), "8180");
        assertEquals(HexUtil.encode(DERUtils.encodeLength(0xFF)), "81FF");
        assertEquals(HexUtil.encode(DERUtils.encodeLength(0x100)), "820100");
        assertEquals(HexUtil.encode(DERUtils.encodeLength(Integer.MAX_VALUE)), "847FFFFFFF");
        try {
            DERUtils.encodeLength(0x80000000);
            fail("Should fail for negative length");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void decodeLength() {
        assertEquals(DERUtils.decodeLength(HexUtil.decode("7F"))[0], 0x7F);
        assertEquals(DERUtils.decodeLength(HexUtil.decode("8180"))[0], 0x80);
        assertEquals(DERUtils.decodeLength(HexUtil.decode("81FF"))[0], 0xFF);
        assertEquals(DERUtils.decodeLength(HexUtil.decode("820100"))[0], 0x100);
        assertEquals(DERUtils.decodeLength(HexUtil.decode("83010203"))[0], 0x010203);
        assertEquals(DERUtils.decodeLength(HexUtil.decode("847FFFFFFF"))[0], Integer.MAX_VALUE);
        try {
            DERUtils.decodeLength(HexUtil.decode("850100000000"));
            fail("Should fail for too big length");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void encodeInteger() {
        assertEquals(HexUtil.encode(new DERInteger(0).getEncoded()), "020100");

        assertEquals(HexUtil.encode(new DERInteger(1).getEncoded()), "020101");
        assertEquals(HexUtil.encode(new DERInteger(13).getEncoded()), "02010D");
        assertEquals(HexUtil.encode(new DERInteger(127).getEncoded()), "02017F");

        // -1 = 127 - 128 -> 127 | 128 = 255 = 0xFF
        assertEquals(HexUtil.encode(new DERInteger(-1).getEncoded()), "0201FF");
        // -13 = 115 - 128 -> 115 | 128 = 243 = 0xF3
        assertEquals(HexUtil.encode(new DERInteger(-13).getEncoded()), "0201F3");
        // -128 = 0 - 128 -> 0 | 128 = 128 = 0x80
        assertEquals(HexUtil.encode(new DERInteger(-128).getEncoded()), "020180");

        assertEquals(HexUtil.encode(new DERInteger(128).getEncoded()), "02020080");
        assertEquals(HexUtil.encode(new DERInteger(256).getEncoded()), "02020100");
        assertEquals(HexUtil.encode(new DERInteger(32767).getEncoded()), "02027FFF");
        assertEquals(HexUtil.encode(new DERInteger(-129).getEncoded()), "0202FF7F");
        assertEquals(HexUtil.encode(new DERInteger(-256).getEncoded()), "0202FF00");
        assertEquals(HexUtil.encode(new DERInteger(-32768).getEncoded()), "02028000");

        assertEquals(HexUtil.encode(new DERInteger(32768).getEncoded()), "0203008000");
        assertEquals(HexUtil.encode(new DERInteger(65536).getEncoded()), "0203010000");
        assertEquals(HexUtil.encode(new DERInteger(8388607).getEncoded()), "02037FFFFF");
        assertEquals(HexUtil.encode(new DERInteger(-32769).getEncoded()), "0203FF7FFF");
        assertEquals(HexUtil.encode(new DERInteger(-65536).getEncoded()), "0203FF0000");
        assertEquals(HexUtil.encode(new DERInteger(-8388608).getEncoded()), "0203800000");

        assertEquals(HexUtil.encode(new DERInteger(8388608).getEncoded()), "020400800000");
        assertEquals(HexUtil.encode(new DERInteger(16777216).getEncoded()), "020401000000");
        assertEquals(HexUtil.encode(new DERInteger(2147483647).getEncoded()), "02047FFFFFFF");
        assertEquals(HexUtil.encode(new DERInteger(Integer.MAX_VALUE).getEncoded()), "02047FFFFFFF");
        assertEquals(HexUtil.encode(new DERInteger(-8388609).getEncoded()), "0204FF7FFFFF");
        assertEquals(HexUtil.encode(new DERInteger(-16777216).getEncoded()), "0204FF000000");
        assertEquals(HexUtil.encode(new DERInteger(Integer.MIN_VALUE).getEncoded()), "020480000000");

        assertEquals(HexUtil.encode(new DERInteger(BigInteger.ZERO).getEncoded()), "020100");
        assertEquals(HexUtil.encode(new DERInteger(BigInteger.ONE).getEncoded()), "020101");
        assertEquals(new BigInteger("80", 16).intValue(), 128);
        assertEquals(new BigInteger("-80", 16).intValue(), -128);
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("-80", 16)).getEncoded()), "020180");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("80", 16)).getEncoded()), "02020080");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("7FFF", 16)).getEncoded()), "02027FFF");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("FF7FFF", 16)).getEncoded()), "020400FF7FFF");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("-FF7FFF", 16)).getEncoded()), "0204FF008001");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("FF000000", 16)).getEncoded()), "020500FF000000");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("-FF000000", 16)).getEncoded()), "0205FF01000000");
        assertEquals(HexUtil.encode(new DERInteger(new BigInteger("0102030405060708090a", 16)).getEncoded()), "020A0102030405060708090A");
    }

    @Test
    public void decodeInteger() {
        assertEquals(((DERInteger) DERUtils.parse(HexUtil.decode("02017F"))).asInt(), 127);
        assertEquals(((DERInteger) DERUtils.parse(HexUtil.decode("0201FF"))).asInt(), -1);
        assertEquals(((DERInteger) DERUtils.parse(HexUtil.decode("020180"))).asInt(), -128);
        assertEquals(((DERInteger) DERUtils.parse(HexUtil.decode("02020080"))).asInt(), 128);
        assertEquals(((DERInteger) DERUtils.parse(HexUtil.decode("0205FF01000000"))).asBigInteger(), new BigInteger("-FF000000", 16));
    }

    @Test
    public void encodeString() {
        assertEquals(HexUtil.encode(new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello!").getEncoded()), "130648656C6C6F21");
        assertEquals(HexUtil.encode(new DEROctetString(DEROctetString.DER_UTF8STRING_TAG, "ąćęłńóśżź").getEncoded()), "0C12C485C487C499C582C584C3B3C59BC5BCC5BA");
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

        assertEquals(HexUtil.encode(new DERUtcTime(DF.parse("2021-06-25 10:11:12")).getEncoded()), "170D3231303632353038313131325A");
    }

    @Test
    public void encodeBoolean() {
        assertEquals(HexUtil.encode(new DERBoolean(true).getEncoded()), "0101FF");
        assertEquals(HexUtil.encode(new DERBoolean(false).getEncoded()), "010100");
    }

    @Test
    public void decodeBoolean() {
        assertTrue(((DERBoolean) DERUtils.parse(HexUtil.decode("0101FF"))).getValue());
        assertTrue(((DERBoolean) DERUtils.parse(HexUtil.decode("010142"))).getValue());
        assertFalse(((DERBoolean) DERUtils.parse(HexUtil.decode("010100"))).getValue());
    }

    @Test
    public void encodeNull() {
        assertEquals(HexUtil.encode(new DERNull().getEncoded()), "0500");
    }

    @Test
    public void decodeNull() {
        assertTrue(DERUtils.parse(HexUtil.decode("0500")) instanceof DERNull);
    }

    @Test
    public void encodeOid() {
        assertEquals(HexUtil.encode(new DERObjectIdentifier("1").getEncoded()), "060128");
        assertEquals(HexUtil.encode(new DERObjectIdentifier("1.0").getEncoded()), "060128");
        assertEquals(HexUtil.encode(new DERObjectIdentifier("1.1").getEncoded()), "060129");
        assertEquals(HexUtil.encode(new DERObjectIdentifier("2.56").getEncoded()), "06028108");
        assertEquals(HexUtil.encode(new DERObjectIdentifier("2.999.3").getEncoded()), "0603883703");
        assertEquals(HexUtil.encode(new DERObjectIdentifier("2.5.4.8").getEncoded()), "0603550408");
        assertEquals(HexUtil.encode(new DERObjectIdentifier("1.2.3.4.5.6").getEncoded()), "06052A03040506");
        assertEquals(HexUtil.encode(new DERObjectIdentifier(DERObjectIdentifier.OID_rsaEncryption).getEncoded()), "06092A864886F70D010101");
        assertEquals(HexUtil.encode(new DERObjectIdentifier(DERObjectIdentifier.OID_sha1WithRSAEncryption).getEncoded()), "06092A864886F70D010105");
    }

    @Test
    public void decodeOid() {
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("060128"))).asOid(), "1.0");
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("060129"))).asOid(), "1.1");
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("06017F"))).asOid(), "2.47");

        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("06028108"))).asOid(), "2.56");
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("0603883703"))).asOid(), "2.999.3");
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("0603550408"))).asOid(), "2.5.4.8");
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("06052A03040506"))).asOid(), "1.2.3.4.5.6");

        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("06092A864886F70D010101"))).asOid(), DERObjectIdentifier.OID_rsaEncryption);
        assertEquals(((DERObjectIdentifier) DERUtils.parse(HexUtil.decode("06092A864886F70D010105"))).asOid(), DERObjectIdentifier.OID_sha1WithRSAEncryption);
    }

    @Test
    public void encodeSequence() {
        DERSequence seq = new DERSequence(new DERObject[] {
                new DERInteger(42),
                new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello")
        });
        assertEquals(HexUtil.encode(seq.getEncoded()), "300A02012A130548656C6C6F");
    }

    @Test
    public void encodeSet() {
        DERSet seq = new DERSet(new DERObject[] {
                new DERInteger(42),
                new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello")
        });
        assertEquals(HexUtil.encode(seq.getEncoded()), "310A02012A130548656C6C6F");
    }

    @Test
    public void encodeTaggedObject() {
        DERTaggedObject object = new DERTaggedObject(DERTaggedObject.TagClass.ContextSpecific, false, (byte) 0, new DERInteger(42));
        assertEquals(HexUtil.encode(object.getEncoded()), "A00302012A");
    }

    @Test
    public void encodeBitString() {
        assertEquals(HexUtil.encode(new DERBitString(new byte[0]).getEncoded()), "030100");
        assertEquals(HexUtil.encode(new DERBitString(new byte[] { 0x00 }).getEncoded()), "03020000");
        assertEquals(HexUtil.encode(new DERBitString(new byte[] { (byte) 0xF0 }).getEncoded()), "030200F0");

        // $ echo -n 301D02012B031800301502012A06092A864886F70D010105130548656C6C6F | xxd -p -r | openssl asn1parse -inform der -i
        //     0:d=0  hl=2 l=  29 cons: SEQUENCE
        //     2:d=1  hl=2 l=   1 prim:  INTEGER           :2B
        //     5:d=1  hl=2 l=  24 prim:  BIT STRING
        // $ echo -n 301D02012B031800301502012A06092A864886F70D010105130548656C6C6F | xxd -p -r | openssl asn1parse -inform der -i -strparse 5
        //     0:d=0  hl=2 l=  21 cons: SEQUENCE
        //     2:d=1  hl=2 l=   1 prim:  INTEGER           :2A
        //     5:d=1  hl=2 l=   9 prim:  OBJECT            :sha1WithRSAEncryption
        //    16:d=1  hl=2 l=   5 prim:  PRINTABLESTRING   :Hello

        DERSequence seq1 = new DERSequence(new DERObject[] {
                new DERInteger(42),
                new DERObjectIdentifier(DERObjectIdentifier.OID_sha1WithRSAEncryption),
                new DEROctetString(DEROctetString.DER_PRINTABLESTRING_TAG, "Hello")
        });
        DERSequence seq2 = new DERSequence(new DERObject[] {
                new DERInteger(43),
                new DERBitString(seq1.getEncoded())
        });
        assertEquals(HexUtil.encode(seq2.getEncoded()), "301D02012B031800301502012A06092A864886F70D010105130548656C6C6F");
    }

}
