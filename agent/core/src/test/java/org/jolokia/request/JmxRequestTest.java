package org.jolokia.request;

/*
 *  Copyright 2009-2010 Roland Huss
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

import javax.management.MalformedObjectNameException;

import org.jolokia.util.PathUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


/**
 * @author roland
 * @since Apr 15, 2010
 */
public class JmxRequestTest {

    @Test
    public void testPathSplitting() throws MalformedObjectNameException {
        List<String> paths = PathUtil.parsePath("hello/world");
        assertEquals(paths.size(),2);
        assertEquals(paths.get(0),"hello");
        assertEquals(paths.get(1),"world");

        paths = PathUtil.parsePath("hello\\/world/second");
        assertEquals(paths.size(),2);
        assertEquals(paths.get(0),"hello/world");
        assertEquals(paths.get(1),"second");
    }

    @Test
    public void testPathGlueing() throws MalformedObjectNameException {
        String path = PathUtil.combineToPath(Arrays.asList("hello/world", "second"));
        assertEquals(path,"hello\\/world/second");
    }
}
