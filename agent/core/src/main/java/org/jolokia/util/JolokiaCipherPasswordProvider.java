package org.jolokia.util;

import java.io.IOException;

/**
 * @author nevenr
 * @since  03/10/2015.
 */
public class JolokiaCipherPasswordProvider {

    private static final String t = "`x%_rDL9T'&ENuyA{LPcc(UDv`NzzY6NZF\"F=rba-9Ftg,HJr.y@E;amfr>B4z<UqQg}2_4kq\\Y@6mNJEpwGx#CT;&?%%.$T_br`(&%3)2vC:5?3f9ptX?KR9kYQu2;#";
    private String keyPath = "META-INF/jolokia-cipher-password-default";

    public String getDefaultKey() throws IOException {
        String key = Resource.getResourceAsString(keyPath);
        if (key != null) {
            return key;
        }
        return t.substring(40,72);
    }
}
