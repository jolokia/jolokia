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

    public static byte[] encodeLength(int size) {
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

        if (size < 128) {
            return new byte[] { lengthEncoded[3] };
        }

        byte[] result = new byte[4 - start + 1];
        result[0] = (byte) ((4 - start) | 0x80);
        System.arraycopy(lengthEncoded, start, result, 1, 4 - start);
        return result;
    }

}
