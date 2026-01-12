/*
 * Copyright 2009-2026 Roland Huss
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class DERContextSpecific implements DERObject {

    private final byte tag;
    private final TagMode mode;
    private final boolean primitive;
    private final DERObject value;

    /**
     * Context-specific object is a "custom tag" with wrapped value inside (EXPLICIT) or replacing an original
     * DER value (IMPLICIT).
     *
     * @param tag
     * @param mode
     * @param primitive
     * @param value
     */
    public DERContextSpecific(byte tag, TagMode mode, boolean primitive, DERObject value) {
        this.tag = tag;
        this.mode = mode;
        this.primitive = primitive;
        this.value = value;

        if (mode == TagMode.EXPLICIT && primitive) {
            throw new IllegalArgumentException("EXPLICIT values can not be primitive");
        }
    }

    @Override
    public byte getTag() {
        return tag;
    }

    @Override
    public String getTagAsString() {
        return "cont [" + tag + "]";
    }

    @Override
    public byte[] getEncoded() {
        byte tag = this.tag;
        if (!primitive) {
            tag |= 0b00100000;
        }
        tag |= (byte) 0b10000000;
        byte[] wrapped = this.value.getEncoded();
        byte[] len = DERUtils.encodeLength(wrapped.length);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            if (mode == TagMode.EXPLICIT) {
                result.write(tag);
                result.write(len);
                result.write(wrapped);
            } else {
                if (primitive) {
                    result.write(tag);
                } else {
                    result.write(tag | 0b00100000);
                }
                result.write(value.getEncoded(), 1, value.getEncoded().length - 1);
            }
        } catch (IOException ignored) {
        }

        return result.toByteArray();
    }

    @Override
    public boolean isPrimitive() {
        return primitive;
    }

    @Override
    public String toString() {
        return getTagAsString();
    }

    public TagMode getMode() {
        return mode;
    }

    public DERObject getValue() {
        return this.value;
    }

    public static DERObject parse(byte tag, TagMode mode, boolean primitive, byte[] encoded, int length, int offsetAfterTag, int lengthLength) {
        if (mode == TagMode.EXPLICIT) {
            // wrapped value
            return new DERContextSpecific(tag, mode, primitive, DERUtils.parse(encoded, offsetAfterTag + lengthLength));
        } else {
            // replaced (IMPLICIT) value
            return new DERContextSpecific(tag, mode, primitive, new DERDirect(Arrays.copyOfRange(encoded, offsetAfterTag - 1, 1 + lengthLength + length)));
        }
    }

    public enum TagMode {
        IMPLICIT, EXPLICIT
    }

}
