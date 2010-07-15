package org.jolokia.client.request;

/**
 * @author roland
 * @since Apr 24, 2010
 */
public enum J4pType {

    // Supported:
    READ("read"),
    LIST("list"),
    WRITE("write"),
    EXEC("exec"),
    VERSION("version"),
    SEARCH("search"),

    // Unsupported:
    REGNOTIF("regnotif"),
    REMNOTIF("remnotif"),
    CONFIG("config");

    private String value;

    J4pType(String pValue) {
        value = pValue;
    }

    public String getValue() {
        return value;
    }
}
