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

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DERUtcTime implements DERObject {

    private static final DateFormat UTC = new SimpleDateFormat("yyMMddHHmmss'Z'");
    public static final byte DER_UTCTIME_TAG = 0x17;

    static {
        UTC.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final Date value;

    public DERUtcTime(Date value) {
        this.value = value;
    }

    @Override
    public byte[] getEncoded() {
        String utctime = UTC.format(value);
        byte[] result = new byte[15];
        result[0] = DER_UTCTIME_TAG;
        result[1] = 0xD;
        try {
            System.arraycopy(utctime.getBytes("UTF-8"), 0, result, 2, 13);
        } catch (UnsupportedEncodingException e) {
            System.arraycopy(utctime.getBytes(), 0, result, 2, 13);
        }
        return result;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

}
