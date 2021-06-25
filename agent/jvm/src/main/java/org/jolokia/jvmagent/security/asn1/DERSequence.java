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

public class DERSequence implements DERObject {

    public static final byte DER_SEQUENCE_TAG = 0x10 | DER_CONSTRUCTED_FLAG;

    private final DERObject[] values;

    public DERSequence(DERObject[] values) {
        this.values = values;
    }

    @Override
    public byte[] getEncoded() {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        ByteArrayOutputStream content = new ByteArrayOutputStream();

        try {
            for (DERObject v : values) {
                content.write(v.getEncoded());
            }

            byte[] bytes = content.toByteArray();
            result.write(DER_SEQUENCE_TAG);
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

}
