package org.jolokia.util;

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

import java.util.List;

import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
/**
 * @author roland
 * @since 19.09.11
 */
public class StringUtilTest {


    Object[] PATH_SPLIT_TEST_DATA = new Object[] {
            "hello/world", asList("hello", "world"),true,
            "hello!/world/yeah",asList("hello/world", "yeah"),true,
            "hello!!/world/yeah",asList("hello!","world","yeah"),true,
            "hello!!!/world/yeah",asList("hello!/world","yeah"),true,
            "hello!!!!world/yeah",asList("hello!!world","yeah"),true,
            "hello!,!!/wor,ld/yeah",asList("hello,!","wor,ld","yeah"),false /* dont do this test reverse because the unnecessarily escaped backslash wont get recreated */
    };

    Object[] COMMA_SPLIT_TEST_DATA = new Object[] {
            "type=s,name=world", asList("type=s", "name=world"),
            "hello!,world,yeah",asList("hello,world", "yeah"),
            "hello!,!/world,yeah",asList("hello,/world","yeah"),
    };



    @Test
    public void pathSplitting() {
        for (int i = 0; i< PATH_SPLIT_TEST_DATA.length; i += 3) {
            List got = StringUtil.parsePath((String) PATH_SPLIT_TEST_DATA[i]);
            assertEquals(got, (List<String>) PATH_SPLIT_TEST_DATA[i+1]);
        }
    }

    @Test
    public void pathCombining() {
        for (int i = 0; i< PATH_SPLIT_TEST_DATA.length; i += 3) {
            // Do reverse test ?
            if ((Boolean) PATH_SPLIT_TEST_DATA[i+2]) {
                String glued = StringUtil.combineToPath((List<String>) PATH_SPLIT_TEST_DATA[i + 1]);
                assertEquals(glued,PATH_SPLIT_TEST_DATA[i]);
            }
        }
    }

    @Test
    public void commaSplitting() {
        for (int i = 0; i< COMMA_SPLIT_TEST_DATA.length; i +=2) {
            List got = StringUtil.split((String) COMMA_SPLIT_TEST_DATA[i],",");
            assertEquals(got, (List<String>) COMMA_SPLIT_TEST_DATA[i+1]);
        }
    }

}
