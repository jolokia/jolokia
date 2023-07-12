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

public class DERInteger implements DERObject {

    private static final byte DER_INTEGER_TAG = 0x02;

    private final int value;

    public DERInteger(int value) {
        this.value = value;
    }

    @Override
    public byte[] getEncoded() {
        // encoded values can be checked using:
        // $ echo 0203FF7FFF | xxd -p -r | openssl asn1parse -inform der -i
        //    0:d=0  hl=2 l=   3 prim: INTEGER           :-8001
        int v = value;
        if (value < 0) {
            v = (-value - 1) & 0x7fffffff;
        }
        if (v < 0x80) {
            // from 020180 = -0x80 to 02017F = 0x7F, including 020100 == 0x00
            return value < 0
                    ? new byte[] { DER_INTEGER_TAG, 0x01, (byte) (0x7f - v | 0x80) }
                    : new byte[] { DER_INTEGER_TAG, 0x01, (byte) v };
        } else if (v < 0x8000) {
            // from 02028000 = -0x8000 to 0202FF7F = -0x81 and
            // from 02020080 = 0x80 to 02027FFF = 0x7FFF
            if (value < 0) {
                v = 0x7fff - v;
                return new byte[] { DER_INTEGER_TAG, 0x02,
                        (byte) (((v & 0x7f00) >> 8) | 0x80),
                        (byte) (v & 0xff)
                };
            }
            return new byte[] { DER_INTEGER_TAG, 0x02,
                    (byte) ((v & 0x7f00) >> 8),
                    (byte) (v & 0xff)
            };
        } else if (v < 0x800000) {
            // from 0203800000 = -0x800000 to 0203FF7FFF = -0x8001 and
            // from 0203008000 = 0x8000 to 02037FFFFF = 0x7FFFFF
            if (value < 0) {
                v = 0x7fffff - v;
                return new byte[] { DER_INTEGER_TAG, 0x03,
                        (byte) (((v & 0x7f0000) >> 16) | 0x80),
                        (byte) ((v & 0xff00) >> 8),
                        (byte) (v & 0xff)
                };
            }
            return new byte[] { DER_INTEGER_TAG, 0x03,
                    (byte) ((v & 0x7f0000) >> 16),
                    (byte) ((v & 0xff00) >> 8),
                    (byte) (v & 0xff)
            };
        } else {
            // from 020380000000 = -0x80000000 to 0203FF7FFFFF = -0x800001 and
            // from 020300800000 = 0x800000 to 02037FFFFFFF = 0x7FFFFFFF
            if (value < 0) {
                v = 0x7fffffff - v;
                return new byte[] { DER_INTEGER_TAG, 0x04,
                        (byte) (((v & 0x7f000000) >> 24) | 0x80),
                        (byte) ((v & 0xff0000) >> 16),
                        (byte) ((v & 0xff00) >> 8),
                        (byte) (v & 0xff)
                };
            }
            return new byte[] { DER_INTEGER_TAG, 0x04,
                    (byte) ((v & 0x7f000000) >> 24),
                    (byte) ((v & 0xff0000) >> 16),
                    (byte) ((v & 0xff00) >> 8),
                    (byte) (v & 0xff)
            };
        }
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

}
