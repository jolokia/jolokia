package org.jolokia.test.util;

import java.util.HashMap;
import java.util.Map;

/**
 * @author roland
 * @since 21.03.13
 */
public class CollectionTestUtil {

    public static Map newMap(Object ... args) {
        Map ret = new HashMap();
        for (int i = 0; i < args.length; i +=2) {
            ret.put(args[i],args[i+1]);
        }
        return ret;
    }
}
