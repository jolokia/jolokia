package org.jolokia.jvmagent;

/*
 * Copyright 2009-2013 Roland Huss
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

import java.net.URI;
import java.net.URISyntaxException;

import org.jolokia.jvmagent.ParsedUri;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 28.09.11
 */
public class ParseUriTest {

    @Test
    public void simple() throws URISyntaxException {
        ParsedUri uri = new ParsedUri(new URI("http://localhost:8080/jolokia/read?test=eins&test=zwei&bla"),"/read");
        String[] test= uri.getParameterMap().get("test");
        assertEquals(test[0],"eins");
        assertEquals(test[1],"zwei");
        assertTrue(uri.getParameterMap().containsKey("bla"));
        assertNull(uri.getParameterMap().get("bla")[0]);
    }
}
