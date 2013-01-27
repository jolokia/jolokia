package org.jolokia.it;

import org.jolokia.jmx.JsonMBean;

/**
 * @author roland
 * @since 27.01.13
 */
@JsonMBean
public class JsonChecking implements JsonCheckingMBean {

    ComplexTestData data;

    public JsonChecking() {
        data = new ComplexTestData();
    }

    public ComplexTestData getData() {
        return data;
    }

    public ComplexTestData execute(String[] args) {
        ComplexTestData data = new ComplexTestData();
        data.setStringArray(args);
        return data;
    }
}
