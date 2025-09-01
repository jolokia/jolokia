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

public class DERUtils {

    private DERUtils() {}

    /**
     * <p>ASN.1 encode length value as length octets according to X.690, 8.1.3 Length octets</p>
     *
     * <p>DER rules permit only the definite form without end marker. {@code int} allows encoding lengths
     * up to and including 0x7FFFFFFF and 5 bytes for encoded length is enough to fit such encoded length value.</p>
     *
     * @param size
     * @return
     */
    public static byte[] encodeLength(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Can't encode negative length");
        }

        int s = size;
        byte[] lengthEncoded = new byte[4];
        for (int i = 3; i >= 0; i--) {
            lengthEncoded[i] = (byte) (s & 0xff);
            s >>>= 8;
        }
        int start;
        for (start = 0; start < 4; start++) {
            if (lengthEncoded[start] != 0) {
                break;
            }
        }

        if (size <= 0x7F) {
            // X.690, 8.1.3.4 In the short form, the length octets shall consist of a single octet in which bit 8 is
            // zero and bits 7 to 1 encode the number of octets in the contents octets
            return new byte[] { lengthEncoded[3] };
        }

        // X.690, 8.1.3.5 In the long form, the length octets shall consist of an initial octet and one or more
        // subsequent octets. The initial octet is 0x80 | <number of consecutive length octets>
        // value 0b11111111 should not be used, so maximum length of the length octets is 0x7E (126), so
        // maximum length is 2^(126*8)
        // but with the integer, the encoded length of the maximum content length is 0x847FFFFFFF
        byte[] result = new byte[4 - start + 1];
        result[0] = (byte) ((4 - start) | 0x80);
        System.arraycopy(lengthEncoded, start, result, 1, 4 - start);
        return result;
    }

    /**
     * Decode length value in the passed byte array
     * @param encoded
     * @return [ the length of the DER value, length of encoded length value ]
     */
    public static int[] decodeLength(byte[] encoded) {
        return decodeLength(encoded, 0);
    }

    /**
     * Decode length value in the passed byte array starting from {@code offset}
     * @param encoded
     * @param offset
     * @return [ the length of the DER value, length of encoded length value ]
     */
    public static int[] decodeLength(byte[] encoded, int offset) {
        if ((encoded[offset] & 0b10000000) == 0) {
            return new int[]{encoded[offset], 1};
        }

        int ll = encoded[offset] & 0x7F;
        if (ll > 4) {
            throw new IllegalArgumentException("Can't decode DER value with length over 2^32");
        }

        // length > 127 is encoded using at least 2 bytes, so +1 for the initial octet with 0x80 flag
        int[] result = new int[]{0, ll + 1};

        while (ll > 0) {
            result[0] |= (encoded[++offset] & 0xff) << ((--ll) * 8);
        }

        return result;
    }

    public static DERObject parse(byte[] encoded) {
        return parse(encoded, 0);
    }

    public static DERObject parse(byte[] encoded, int offset) {
        if (encoded == null || encoded.length < 2) {
            throw new IllegalArgumentException("Can't DER-decode null/empty/insufficient byte array");
        }

        byte id = encoded[offset];
        int cls = id & 0b11000000;
        boolean primitive = (id & DERObject.DER_CONSTRUCTED_FLAG) == 0;
        int tag = id & 0b00011111;

        if (tag == 0b00011111) {
            throw new IllegalArgumentException("Can't decode tags with number higher than 30");
        }

        int[] lo = decodeLength(encoded, offset + 1);

        switch (tag) {
            case 0x01: // BOOLEAN
                return DERBoolean.parse(encoded, offset + 1);
            case 0x02: // INTEGER
                return DERInteger.parse(encoded, lo[0], offset + 1 + lo[1]);
            case 0x03: // BIT STRING
                return DERBitString.parse(encoded, lo[0], offset + 1 + lo[1]);
            case 0x04: // OCTET STRING
                break;
            case 0x05: // NULL
                return new DERNull();
            case 0x06: // OBJECT IDENTIFIER
                return DERObjectIdentifier.parse(encoded, lo[0], offset + 1 + lo[1]);
            case 0x0A: // ENUMERATED
                break;
            case 0x0C: // UTF8String
                break;
            case 0x10: // SEQUENCE (Constructed)
                return DERSequence.parse(encoded, lo[0], offset + 1 + lo[1]);
            case 0x11: // SET (Constructed)
                break;
            case 0x12: // NumericString
                break;
            case 0x13: // PrintableString
                break;
            case 0x16: // IA5String
                break;
            case 0x17: // UTCTime
                break;
            case 0x1A: // VisibleString
                break;
            case 0x1B: // GeneralString
                break;
            case 0x1C: // UniversalString
                break;
            case 0x1E: // BMPString
                break;
        }

        throw new IllegalArgumentException("Unknown ASN.1 tag " + id);
    }

}
