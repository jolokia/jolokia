package org.jolokia.server.core;

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

import org.testng.Assert;

import static org.testng.Assert.*;

/**
 * Check whether the pom.xml version and the code version is
 * the same
 *
 * @author roland
 * @since 14.01.13
 */
public class VersionTest {

    //@Test
    public void verifyVersion() {
        Assert.assertEquals(Version.getAgentVersion(), System.getProperty("project.version"));
        assertNotNull(Version.getProtocolVersion());
    }
}
