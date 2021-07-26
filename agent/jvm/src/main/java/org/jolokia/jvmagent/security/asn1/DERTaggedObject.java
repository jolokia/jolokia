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

/**
 * An implementation of tagged object according to X.690, 8.1.2 Identifier octets
 */
public class DERTaggedObject implements DERObject {

    private final TagClass tagClass;
    private final boolean primitive;
    private final byte tagNumber;
    private final DERObject value;

    public DERTaggedObject(TagClass tagClass, boolean primitive, int tagNumber, DERObject value) {
        this.tagClass = tagClass;
        this.primitive = primitive;
        this.tagNumber = (byte) (tagNumber & 0x1F);
        this.value = value;
    }

    @Override
    public byte[] getEncoded() {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        ByteArrayOutputStream content = new ByteArrayOutputStream();

        try {
            content.write(value.getEncoded());

            byte[] bytes = content.toByteArray();
            byte tag = tagClass.encoded;
            if (!primitive) {
                tag |= 0x20;
            }
            tag |= tagNumber;
            result.write(tag);
            result.write(DERUtils.encodeLength(bytes.length));
            result.write(bytes);
        } catch (IOException ignored) {
        }

        return result.toByteArray();
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    public enum TagClass {
        Universal((byte) 0x00),
        Application((byte) 0x40),
        ContextSpecific((byte) 0x80),
        Private((byte) 0xC0);

        private final byte encoded;

        TagClass(byte encoded) {
            this.encoded = encoded;
        }

        public byte getEncoded() {
            return encoded;
        }
    }

}
