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

public class DERBitString implements DERObject {

    public static final byte DER_BITSTRING_TAG = 0x03;

    private final byte[] value;

    public DERBitString(byte[] value) {
        this.value = value;
    }

    @Override
    public byte[] getEncoded() {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.write(0x0); // 0 unused bits in last octet of the content - we only support full bytes being encoded
        try {
            content.write(value);
            result.write(DER_BITSTRING_TAG);
            result.write(DERUtils.encodeLength(content.size()));
            result.write(content.toByteArray());
        } catch (IOException ignored) {
        }

        return result.toByteArray();
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

}
