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
import java.io.StringWriter;

public class HexUtil {

    private HexUtil() {}

    public static String encode(byte[] bytes) {
        StringWriter sw = new StringWriter();
        for (byte b : bytes) {
            sw.append(String.format("%02x", b));
        }

        return sw.toString().toUpperCase();
    }

    public static byte[] decode(String encoded) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (encoded.length() % 2 == 1) {
            encoded = "0" + encoded;
        }
        encoded = encoded.toLowerCase();

        char[] chars = encoded.toCharArray();
        for (int pos = 0; pos < encoded.length(); pos += 2) {
            int c1 = Character.digit(chars[pos], 16);
            int c2 = Character.digit(chars[pos + 1], 16);
            baos.write(c1 << 4 | c2);
        }

        return baos.toByteArray();
    }

}
