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

import java.io.UnsupportedEncodingException;

public class DEROctetString implements DERObject {

    // ISO/IEC 10646 - Unicode
    public static final byte DER_UTF8STRING_TAG = 0x0C;
    // X.680, 41.4: A-Za-z0-9 '()+,-./:=?
    public static final byte DER_PRINTABLESTRING_TAG = 0x13;
    // ISO/IEC 646 - ISO 7-bit coded character set
    public static final byte DER_IA5STRING_TAG = 0x16;

    private final byte tag;
    private final String value;

    public DEROctetString(byte tag, String value) {
        if (value.length() > 0xFFFF) {
            throw new IllegalArgumentException("Can't DER encoded Strings longer than 64KiB");
        }
        this.tag = tag;
        this.value = value;
    }

    @Override
    public byte[] getEncoded() {
        byte[] bytes;
        try {
            bytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            bytes = value.getBytes();
        }
        if (bytes.length < 128) {
            byte[] result = new byte[bytes.length + 2];
            result[0] = tag;
            result[1] = (byte) bytes.length;
            System.arraycopy(bytes, 0, result, 2, bytes.length);
            return result;
        } else if (bytes.length <= 256) {
            byte[] result = new byte[bytes.length + 3];
            result[0] = tag;
            result[1] = (byte) 0x81;
            result[2] = (byte) bytes.length;
            System.arraycopy(bytes, 0, result, 3, bytes.length);
            return result;
        } else /* if (bytes.length <= 65536) */ {
            byte[] result = new byte[bytes.length + 4];
            result[0] = tag;
            result[1] = (byte) 0x82;
            result[2] = (byte) ((bytes.length & 0xff00) >> 8);
            result[3] = (byte) (bytes.length & 0xff);
            System.arraycopy(bytes, 0, result, 4, bytes.length);
            return result;
        }
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

}
