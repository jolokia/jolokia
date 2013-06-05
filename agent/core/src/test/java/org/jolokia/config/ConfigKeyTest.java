package org.jolokia.config;

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

import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 17.01.13
 */
public class ConfigKeyTest {

    @Test
    public void extractKey() {
        Map<String,String> map = new HashMap<String, String>();
        map.put(ConfigKey.MAX_OBJECTS.getKeyValue(),"4711");
        map.put(ConfigKey.CANONICAL_NAMING.getKeyValue(),"true");
        map.put("blub","bla");
        Configuration config = new Configuration();
        config.updateGlobalConfiguration(map);
        assertEquals(config.size(),2);
        assertEquals(config.get(ConfigKey.MAX_OBJECTS),"4711");
        assertEquals(config.get(ConfigKey.CANONICAL_NAMING),"true");
    }
}
