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

public interface DERObject {

    byte DER_CONSTRUCTED_FLAG = 0x20;

    byte[] getEncoded();

    /**
     * Whether the object is encoded as ASN.1 primitive (see 3.10 "primitive encoding"). If not primitive, the
     * object is <em>constructed</em>, which means its contents octets are the complete encoding of one or
     * more data values.
     *
     * @return {@code true} if the object encodes its value directly
     */
    boolean isPrimitive();

}
