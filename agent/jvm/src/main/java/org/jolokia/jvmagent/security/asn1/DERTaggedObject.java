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

    /**
     * Tagged object is an encoded content of a {@link DERObject} associated with:<ul>
     *     <li>Identifier octets - ASN.1 tag (class + number) of the type of the data value. This is one octet
     *         for tags with a number between 0 and 30 and more octets for tags with numbers greater than 30.</li>
     *     <li>Length octets - encode the length of the content octets which encode actual value.</li>
     * </ul>
     *
     * @param tagClass
     * @param primitive specifies whether the type is primitive or constructed. Each type is specified explicitly as
     *                  primitive or constructed (for example SEQUENCE is constructed, while BOOLEAN is primitive)
     * @param tagNumber
     * @param value
     */
    public DERTaggedObject(TagClass tagClass, boolean primitive, int tagNumber, DERObject value) {
        if (tagNumber > 30) {
            throw new IllegalArgumentException("Only tag numbers 0-30 are supported");
        }

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
                tag |= DERObject.DER_CONSTRUCTED_FLAG;
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

    /**
     * <p>Class of a tag.</p>
     * <p>See: X.690, 8.1.2 Identifier octets</p>
     */
    public enum TagClass {
        /** Universal tag class is always used for universal tag numbers (00-30) */
        Universal((byte) 0b00000000),
        Application((byte) 0b01000000),
        ContextSpecific((byte) 0b10000000),
        Private((byte) 0b11000000);

        private final byte encoded;

        TagClass(byte encoded) {
            this.encoded = encoded;
        }

        public byte getEncoded() {
            return encoded;
        }
    }

}
