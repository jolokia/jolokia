package org.jolokia.util;/*
 * 
 * Copyright 2014 Roland Huss
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

import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 13/09/15
 */
public class Base64UtilTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decodeNull() {
        Base64Util.decode(null);
    }

    @Test
    public void decodeEmpty() {
        assertEquals(Base64Util.decode("").length, 0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decodeToSmall() {
        assertEquals(Base64Util.decode("abc").length,0);
    }


    @Test
    public void identity() {
        String text = "The Milkiman of Human Kindness";
        assertEquals(text.getBytes(),Base64Util.decode(Base64Util.encode(text.getBytes())));
    }

    @Test
    public void decodeBig() {
        byte[] res = Base64Util.decode("TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQsIGNvbnNldGV0dXIgc2FkaXBzY2luZyBlbGl0ciwg\n" +
                                       "c2VkIGRpYW0gbm9udW15IGVpcm1vZCB0ZW1wb3IgaW52aWR1bnQgdXQgbGFib3JlIGV0IGRvbG9y\n" +
                                       "ZSBtYWduYSBhbGlxdXlhbSBlcmF0LCBzZWQgZGlhbSB2b2x1cHR1YS4gQXQgdmVybyBlb3MgZXQg\n" +
                                       "YWNjdXNhbSBldCBqdXN0byBkdW8gZG9sb3JlcyBldCBlYSByZWJ1bS4gU3RldCBjbGl0YSBrYXNk\n" +
                                       "IGd1YmVyZ3Jlbiwgbm8gc2VhIHRha2ltYXRhIHNhbmN0dXMgZXN0IExvcmVtIGlwc3VtIGRvbG9y\n" +
                                       "IHNpdCBhbWV0LiBMb3JlbSBpcHN1bSBkb2xvciBzaXQgYW1ldCwgY29uc2V0ZXR1ciBzYWRpcHNj\n" +
                                       "aW5nIGVsaXRyLCBzZWQgZGlhbSBub251bXkgZWlybW9kIHRlbXBvciBpbnZpZHVudCB1dCBsYWJv\n" +
                                       "cmUgZXQgZG9sb3JlIG1hZ25hIGFsaXF1eWFtIGVyYXQsIHNlZCBkaWFtIHZvbHVwdHVhLiBBdCB2\n" +
                                       "ZXJvIGVvcyBldCBhY2N1c2FtIGV0IGp1c3RvIGR1byBkb2xvcmVzIGV0IGVhIHJlYnVtLiBTdGV0\n" +
                                       "IGNsaXRhIGthc2QgZ3ViZXJncmVuLCBubyBzZWEgdGFraW1hdGEgc2FuY3R1cyBlc3QgTG9yZW0g\n" +
                                       "aXBzdW0gZG9sb3Igc2l0IGFtZXQuIDEyMzQ1IS84Ly8vMzQ1KiY=");
        assertEquals(new String(res), "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod " +
                                      "tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero " +
                                      "eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata " +
                                      "sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing " +
                                      "elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed " +
                                      "diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd " +
                                      "gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. 12345!/8///345*&");
    }
}
