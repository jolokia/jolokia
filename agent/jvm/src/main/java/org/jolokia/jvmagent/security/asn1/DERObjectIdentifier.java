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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class DERObjectIdentifier implements DERObject {

    public static final byte DER_OBJECTIDENTIFIER_TAG = 0x06;

    // http://oid-info.com/cgi-bin/display?tree=2.5.4#focus
    public static final String OID_countryName = "2.5.4.6";
    public static final String OID_stateOrProvinceName = "2.5.4.8";
    public static final String OID_localityName = "2.5.4.7";
    public static final String OID_organizationName = "2.5.4.10";
    public static final String OID_organizationalUnitName = "2.5.4.11";
    public static final String OID_commonName = "2.5.4.3";

    public static final String OID_rsaEncryption = "1.2.840.113549.1.1.1";
    public static final String OID_sha1WithRSAEncryption = "1.2.840.113549.1.1.5";

    private final int[] values;

    public DERObjectIdentifier(String value) {
        String[] vt = value.split("\\.");
        values = new int[vt.length];
        int p = 0;
        for (String v : vt) {
            try {
                if ("".equals(v.trim())) {
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
                maxResult[i] |= 0x80;
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

}
