/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter;

import java.util.Map;

import org.jolokia.client.JolokiaClientOption;
import org.testng.annotations.Ignore;

/**
 * Tests for connections to Jolokia Agent which didn't provide {@link javax.management.openmbean.OpenType}
 * information - very helpful when reconstructing the values.
 */
@Ignore
public class JmxConnectorWithoutOpenTypesTest extends JmxConnectorTest {

    @Override
    protected Map<String, Object> env() {
        return Map.of(JolokiaClientOption.OPEN_TYPES.asSystemProperty(), "false");
    }

}
