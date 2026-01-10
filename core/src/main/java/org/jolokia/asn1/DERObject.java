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

public interface DERObject {

    /*
     * | --------- | -------------------------------- | ----------- | ----------------------------------------------- |
     * | Tag (Hex) | Universal Type                   | DER Form    | Notes                                           |
     * | --------- | -------------------------------- | ----------- | ----------------------------------------------- |
     * | 0x00      | `EOC` (End-of-Content)           | —           | Not used in DER (no indefinite-length).         |
     * | 0x01      | `BOOLEAN`                        | Primitive   | Always primitive.                               |
     * | 0x02      | `INTEGER`                        | Primitive   | Always primitive.                               |
     * | 0x03      | `BIT STRING`                     | Primitive   | Must be primitive in DER (no constructed form). |
     * | 0x04      | `OCTET STRING`                   | Primitive   | Must be primitive in DER (no constructed form). |
     * | 0x05      | `NULL`                           | Primitive   | Always primitive, length = 0.                   |
     * | 0x06      | `OBJECT IDENTIFIER`              | Primitive   | Always primitive.                               |
     * | 0x07      | `ObjectDescriptor`               | Primitive   | Rarely used.                                    |
     * | 0x08      | `EXTERNAL`                       | Constructed | Always constructed.                             |
     * | 0x09      | `REAL` (FLOAT)                   | Primitive   | Always primitive.                               |
     * | 0x0A      | `ENUMERATED`                     | Primitive   | Same encoding rules as INTEGER.                 |
     * | 0x0B      | `EMBEDDED PDV`                   | Constructed | Always constructed.                             |
     * | 0x0C      | `UTF8String`                     | Primitive   | Must be primitive in DER.                       |
     * | 0x0D      | `RELATIVE-OID`                   | Primitive   | Always primitive.                               |
     * | 0x0E      | Reserved                         | —           | Not used.                                       |
     * | 0x0F      | Reserved                         | —           | Not used.                                       |
     * | 0x10      | `SEQUENCE` / `SEQUENCE OF`       | Constructed | Always constructed.                             |
     * | 0x11      | `SET` / `SET OF`                 | Constructed | Always constructed.                             |
     * | 0x12      | `NumericString`                  | Primitive   | Must be primitive in DER.                       |
     * | 0x13      | `PrintableString`                | Primitive   | Must be primitive in DER.                       |
     * | 0x14      | `TeletexString` / `T61String`    | Primitive   | Must be primitive in DER.                       |
     * | 0x15      | `VideotexString`                 | Primitive   | Must be primitive in DER.                       |
     * | 0x16      | `IA5String`                      | Primitive   | Must be primitive in DER.                       |
     * | 0x17      | `UTCTime`                        | Primitive   | Always primitive.                               |
     * | 0x18      | `GeneralizedTime`                | Primitive   | Always primitive.                               |
     * | 0x19      | `GraphicString`                  | Primitive   | Must be primitive in DER.                       |
     * | 0x1A      | `VisibleString` / `ISO646String` | Primitive   | Must be primitive in DER.                       |
     * | 0x1B      | `GeneralString`                  | Primitive   | Must be primitive in DER.                       |
     * | 0x1C      | `UniversalString`                | Primitive   | Must be primitive in DER.                       |
     * | 0x1D      | `CHARACTER STRING`               | Constructed | Always constructed.                             |
     * | 0x1E      | `BMPString`                      | Primitive   | Must be primitive in DER.                       |
     * | --------- | -------------------------------- | ----------- | ----------------------------------------------- |
     */

    // X.690, 3.2 constructed encoding: A data value encoding in which the contents octets are the complete
    // encoding of one or more data values.
    // bit 6 of the identifier octet.
    byte DER_CONSTRUCTED_FLAG = 0b00100000;

    byte[] getEncoded();

    /**
     * Whether the object is encoded as ASN.1 primitive (see 3.10 "primitive encoding"). If not primitive, the
     * object is <em>constructed</em>, which means its contents octets are the complete encoding of one or
     * more data values.
     *
     * @return {@code true} if the object encodes its value directly
     */
    boolean isPrimitive();

    byte getTag();

    String getTagAsString();

}
