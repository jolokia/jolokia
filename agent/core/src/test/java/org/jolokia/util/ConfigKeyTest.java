package org.jolokia.util;

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
        Map<ConfigKey,String> config = ConfigKey.extractConfig(map);
        assertEquals(config.size(),2);
        assertEquals(config.get(ConfigKey.MAX_OBJECTS),"4711");
        assertEquals(config.get(ConfigKey.CANONICAL_NAMING),"true");
    }
}
