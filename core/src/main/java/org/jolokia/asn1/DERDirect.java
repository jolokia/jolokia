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

package org.jolokia.asn1;

public class DERDirect implements DERObject {

    private final byte[] value;

    public DERDirect(byte[] value) {
        this.value = value;
    }

    @Override
    public byte getTag() {
        return -1;
    }

    @Override
    public String getTagAsString() {
        return "<direct>";
    }

    @Override
    public byte[] getEncoded() {
        return value;
    }

    @Override
    public String toString() {
        return "<direct value>";
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

}
