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

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.jolokia.util.EscapeUtil.*;
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
    public void escaping() {
        assertEquals(EscapeUtil.escape("hello\\world,how are you?",CSV_ESCAPE,","),"hello\\\\world\\,how are you?");
    }

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
