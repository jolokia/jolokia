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

public class DERNull implements DERObject {

    public static final byte DER_NULL_TAG = 0x05;

    public DERNull() {
    }

    @Override
    public byte[] getEncoded() {
        return new byte[] { DER_NULL_TAG, 0x00 };
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

}
