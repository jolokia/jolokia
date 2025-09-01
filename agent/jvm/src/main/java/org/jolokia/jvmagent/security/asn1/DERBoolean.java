/*
 * Copyright 2009-2025 Roland Huss
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

public class DERBoolean implements DERObject {

    private static final byte DER_BOOLEAN_TAG = 0x01;

    private final boolean value;

    public DERBoolean(boolean value) {
        this.value = value;
    }

    @Override
    public byte[] getEncoded() {
        return new byte[] { DER_BOOLEAN_TAG, 0x01, this.value ? (byte) 0xFF : 0x00 };
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    public boolean getValue() {
        return value;
    }

    public static DERBoolean parse(byte[] encoded, int offset) {
        return new DERBoolean(encoded[offset + 1] != 0);
    }

}
