package org.jolokia.server.core.util;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

import static org.jolokia.server.core.util.EscapeUtil.*;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 19.09.11
 */
public class EscapeUtilTest {


    Object[] PATH_SPLIT_TEST_DATA = new Object[] {
            PATH_ESCAPE + PATH_ESCAPE + PATH_ESCAPE + PATH_ESCAPE,asList(PATH_ESCAPE + PATH_ESCAPE),true,
            "hello" + PATH_ESCAPE + PATH_ESCAPE,asList("hello" + PATH_ESCAPE),true,
            "hello/world", asList("hello", "world"),true,
            "hello" + PATH_ESCAPE + "/world/yeah",asList("hello/world", "yeah"),true,
            "hello" + PATH_ESCAPE + PATH_ESCAPE + "/world/yeah",asList("hello" + PATH_ESCAPE,"world","yeah"),true,
            "hello" + PATH_ESCAPE + PATH_ESCAPE + PATH_ESCAPE + "/world/yeah",asList("hello" + PATH_ESCAPE + "/world","yeah"),true,
            "hello" + PATH_ESCAPE + PATH_ESCAPE + PATH_ESCAPE + PATH_ESCAPE + "world/yeah",asList("hello" + PATH_ESCAPE + PATH_ESCAPE + "world","yeah"),true,
            "hello" + PATH_ESCAPE + "," + PATH_ESCAPE + PATH_ESCAPE + "/wor,ld/yeah",asList("hello," + PATH_ESCAPE,"wor,ld","yeah"),false /* dont do this test reverse because the unnecessarily escaped backslash wont get recreated */
    };

    Object[] COMMA_SPLIT_TEST_DATA = new Object[] {
            "type=s,name=world", asList("type=s", "name=world"),
            "hello\\,world,yeah",asList("hello,world", "yeah"),
            "hello\\,\\/world,yeah",asList("hello,/world","yeah"),
    };



    @Test
    public void pathSplitting() {
        for (int i = 0; i< PATH_SPLIT_TEST_DATA.length; i += 3) {
            List got = EscapeUtil.parsePath((String) PATH_SPLIT_TEST_DATA[i]);
            assertEquals(got, (List<String>) PATH_SPLIT_TEST_DATA[i+1]);
        }
    }

    @Test
    public void pathCombining() {
        for (int i = 0; i< PATH_SPLIT_TEST_DATA.length; i += 3) {
            // Do reverse test ?
            if ((Boolean) PATH_SPLIT_TEST_DATA[i+2]) {
                String glued = EscapeUtil.combineToPath((List<String>) PATH_SPLIT_TEST_DATA[i + 1]);
                assertEquals(glued,PATH_SPLIT_TEST_DATA[i]);
            }
        }
    }

    @Test
    public void commaSplitting() {
        for (int i = 0; i< COMMA_SPLIT_TEST_DATA.length; i +=2) {
            List got = EscapeUtil.split((String) COMMA_SPLIT_TEST_DATA[i], CSV_ESCAPE, ",");
            assertEquals(got, (List<String>) COMMA_SPLIT_TEST_DATA[i+1]);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decodeNull() {
        EscapeUtil.decodeBase64(null);
    }

    @Test
    public void decodeEmpty() {
        assertEquals(EscapeUtil.decodeBase64("").length,0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decodeToSmall() {
        assertEquals(EscapeUtil.decodeBase64("abc").length,0);
    }

    @Test
    public void decodeBig() {
        byte[] res = EscapeUtil.decodeBase64("TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQsIGNvbnNldGV0dXIgc2FkaXBzY2luZyBlbGl0ciwg\n" +
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
        assertEquals(new String(res),"Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod " +
                                     "tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero " +
                                     "eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata " +
                                     "sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing " +
                                     "elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed " +
                                     "diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd " +
                                     "gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. 12345!/8///345*&");
    }

    @Test
    public void stackOverflowError() {
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 15000; i++) {
            longString.append("!!");
        }
        List<String> arguments = Arrays.asList(longString.toString());

        String path = EscapeUtil.combineToPath(arguments);

        List<String> parsed = EscapeUtil.parsePath(path); // StackOverflowError inside this method
        assertEquals(parsed.size(),1);
        assertEquals(parsed.get(0),longString.toString());
    }
}
